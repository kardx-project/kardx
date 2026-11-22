package io.github.kardx.internal.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.mockmvc.andDocument
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import java.io.File
import java.nio.file.Files

/** [DocumentedExtras] 의 문서 클래스. 스니펫은 `documented-extras-docs/<메서드>/` 에 생긴다 */
class DocumentedExtrasDocs

/**
 * [controller] 를 MockMvc(standalone)로 부르고 `andDocument` 로 문서화한 뒤 `openapi-extras.json`·`resource.json` 을 읽는다.
 * 핸들러 기본값(API 버전 등)까지 실제 문서화 경로를 그대로 탄다.
 */
class DocumentedExtras(controller: Any) {
    private val output: File = Files.createTempDirectory("documented-extras").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    /** [method] 이름으로 문서화하고 그 `openapi-extras.json` 을 읽는다. 파일이 없으면 빈 맵. */
    fun run(method: String, request: RequestBuilder, block: DocumentationBuilder.() -> Unit): Map<*, *> {
        documentation.beforeTest(DocumentedExtrasDocs::class.java, method)

        try {
            mockMvc.perform(request)
                .andDocument(block)
        } finally {
            documentation.afterTest()
        }

        val file = file(method, OpenApiOperationExtras.FILE_NAME)

        return if (file.isFile) ObjectMapper().readValue(file, Map::class.java) else emptyMap<Any, Any>()
    }

    /** [method] 의 스니펫 파일. */
    fun file(method: String, name: String): File = File(output, "documented-extras-docs/$method/$name")
}

/** JSON 객체 목록으로 본다. */
@Suppress("UNCHECKED_CAST")
fun Any?.objects(): List<Map<String, Any?>> = this as List<Map<String, Any?>>

/** JSON 객체로 본다. */
@Suppress("UNCHECKED_CAST")
fun Any?.obj(): Map<String, Any?> = this as Map<String, Any?>
