package io.github.kardx.internal.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.annotation.AsciiDescription
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.cookieMeans
import io.github.kardx.dsl.partMeans
import io.github.kardx.extractor.requestFieldsOf
import io.github.kardx.extractor.responseFieldsOf
import io.github.kardx.internal.documentation.toSpec
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.servlet.http.Cookie
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.DisplayName
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files

data class UploadMeta(@field:NotBlank val alt: String)

@RestController
class ExtrasController {
    @PostMapping("/images")
    fun upload(@RequestPart("file") file: MultipartFile, @RequestPart("meta") meta: MultipartFile): String =
        "ok ${file.size + meta.size}"

    private val items = listOf("a", "b", "c")

    @GetMapping("/count")
    fun count(): Long = items.size.toLong()
}

@DisplayName("부가 정보")
@AsciiDescription("multipart·쿠키·값 본문")
class ExtrasDocs

/** 테스트 클래스 [ExtrasDocs] 로 문서화하고 남은 `openapi-extras.json` 을 읽는다. */
private class Extras {
    val output: File = Files.createTempDirectory("extras").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(ExtrasController())
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    fun run(method: String, request: RequestBuilder, block: DocumentationBuilder.() -> Unit): Map<*, *> {
        documentation.beforeTest(ExtrasDocs::class.java, method)

        try {
            val resource = DocumentationBuilder().apply(block)
                .toSpec()
                .openApiResource(securitySchemes = true)

            mockMvc.perform(request)
                .andDo(document("{class-name}/{method-name}", OpenApiOperationExtras(resource)))
        } finally {
            documentation.afterTest()
        }

        val file = File(output, "extras-docs/$method/${OpenApiOperationExtras.FILE_NAME}")

        return ObjectMapper().readValue(file, Map::class.java)
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.list(): List<Map<String, Any?>> = this as List<Map<String, Any?>>

class OpenApiOperationExtrasTest :
    BehaviorSpec({
        given("multipart 파트와 요청 쿠키") {
            val file = MockMultipartFile("file", "a.png", MediaType.IMAGE_PNG_VALUE, ByteArray(4))
            val meta = MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE, """{"alt":"a"}""".toByteArray())
            val json = Extras().run(
                "upload",
                multipart("/images").file(file)
                    .file(meta)
                    .cookie(Cookie("SESSION", "s")),
            ) {
                requestParts("file" partMeans "이미지", "meta" partMeans "메타데이터")
                requestPartFields("meta", requestFieldsOf<UploadMeta>())
                requestCookies("SESSION" cookieMeans "세션")
            }

            then("파일 파트는 binary, 본문 필드를 적은 파트는 스키마와 Content-Type") {
                val parts = json["requestParts"].list()

                parts[0]["schema"] shouldBe mapOf("type" to "string", "format" to "binary")
                parts[0]["description"] shouldBe "이미지"
                val schema = parts[1]["schema"] as Map<*, *>
                val alt = (schema["properties"] as Map<*, *>)["alt"] as Map<*, *>

                parts[1]["contentType"] shouldBe "application/json"
                schema["required"] shouldBe listOf("alt")
                alt["type"] shouldBe "string"
                alt["minLength"] shouldBe 1
            }

            then("쿠키 이름·설명·optional") {
                json["requestCookies"] shouldBe
                    listOf(mapOf("name" to "SESSION", "description" to "세션", "optional" to false))
            }

            then("태그 설명은 테스트 클래스의 @AsciiDescription") {
                json["tag"] shouldBe mapOf("name" to "부가 정보", "description" to "multipart·쿠키·값 본문")
            }
        }

        given("필드 없는 값 본문") {
            val json = Extras().run("count", get("/count")) { responseFields(responseFieldsOf<Long>()) }

            then("타입에서 만든 스키마를 남긴다") {
                json["responseBody"] shouldBe mapOf("type" to "integer", "format" to "int64")
            }
        }
    })
