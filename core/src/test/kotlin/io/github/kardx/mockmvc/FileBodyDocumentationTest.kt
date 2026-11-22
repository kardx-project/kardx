package io.github.kardx.mockmvc

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.SnippetFile
import io.github.kardx.diagnostics.RestDocsErrorCode.INVALID_FILE_BODY
import io.github.kardx.dsl.DocumentationBuilder
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.partMeans
import io.github.kardx.dsl.type
import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.github.kardx.internal.documentation.toSpec
import io.github.kardx.internal.openapi.OpenApiOperationExtras
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.templates.TemplateFormats
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.nio.file.Files

@RestController
class FileController {
    @GetMapping("/manual")
    fun manual(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/pdf;charset=UTF-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"manual.pdf\"")
        .body(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0xE2.toByte(), 0xE3.toByte()))

    @GetMapping("/one-byte")
    fun oneByte(): ResponseEntity<ByteArray> = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(byteArrayOf(0x01))

    @GetMapping("/export.csv")
    fun csv(): ResponseEntity<String> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv"))
        .body("id,name\n1,keyboard\n")

    @GetMapping("/item")
    fun item(): Map<String, String> = mapOf("name" to "keyboard")

    @PutMapping("/image")
    fun upload(@RequestBody image: ByteArray): String = "${image.size}"
}

/** 한 테스트 메서드의 스니펫과 `openapi-extras.json` 을 임시 디렉터리에 남긴다. */
private class FileDocs(markdown: Boolean = false) {
    val output: File = Files.createTempDirectory("file-bodies").toFile()
    private val documentation = ManualRestDocumentation(output.absolutePath)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(FileController())
        .apply<StandaloneMockMvcBuilder>(
            documentationConfiguration(documentation)
                .snippets()
                .apply {
                    if (markdown) withTemplateFormat(TemplateFormats.markdown())
                },
        )
        .build()

    fun run(method: String, request: RequestBuilder, block: DocumentationBuilder.() -> Unit = {}) {
        documentation.beforeTest(FileBodyDocumentationTest::class.java, method)

        try {
            mockMvc.perform(request)
                .andDocument(block)
        } finally {
            documentation.afterTest()
        }
    }

    fun snippet(method: String, name: String): String = file(method, name).readText()

    fun exists(method: String, name: String): Boolean = file(method, name).isFile

    fun extras(method: String): Map<*, *> =
        ObjectMapper().readValue(file(method, OpenApiOperationExtras.FILE_NAME), Map::class.java)

    private fun file(method: String, name: String) = File(output, "file-body-documentation-test/$method/$name")
}

private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

class FileBodyDocumentationTest :
    BehaviorSpec({
        given("responseFile·requestFile 선언 검사") {
            fun declare(block: DocumentationBuilder.() -> Unit) = DocumentationBuilder().apply(block)
                .toSpec()

            then("빈 설명과 두 번 선언은 거부한다") {
                shouldThrow<IllegalArgumentException> { declare { responseFile(" ") } }
                    .message shouldBe INVALID_FILE_BODY.message("responseFile needs a non-blank description.")
                shouldThrow<IllegalArgumentException> { declare { requestFile("") } }
                    .message shouldBe INVALID_FILE_BODY.message("requestFile needs a non-blank description.")
                shouldThrow<IllegalArgumentException> {
                    declare {
                        responseFile("a")
                        responseFile("b")
                    }
                }.message shouldContain "responseFile is declared more than once"
            }

            then("본문 필드·파트와 함께 쓰면 순서와 상관없이 실패한다") {
                shouldThrow<IllegalArgumentException> {
                    declare {
                        responseFields("name" type STRING means "이름")
                        responseFile("설명서")
                    }
                }.message shouldContain "responseFile cannot be used together with responseFields"
                shouldThrow<IllegalArgumentException> {
                    declare {
                        requestFile("이미지")
                        requestFields("name" type STRING means "이름")
                    }
                }.message shouldContain "requestFile cannot be used together with requestFields"
                shouldThrow<IllegalArgumentException> {
                    declare {
                        requestFile("이미지")
                        requestParts("file" partMeans "파일")
                    }
                }.message shouldContain "requestFile cannot be used together with requestParts"
            }

            then("선언한 설명은 spec 에 남는다") {
                val spec = declare {
                    requestFile("이미지")
                    responseFile("설명서")
                }

                spec.requestFile shouldBe "이미지"
                spec.responseFile shouldBe "설명서"
            }
        }

        given("바이너리 응답을 responseFile 없이 문서화하면") {
            val docs = FileDocs()

            docs.run("plain-pdf", get("/manual"))

            then("response-file 표에 charset 없는 미디어 타입, 파일 이름, 크기가 있고 설명 행은 없다") {
                docs.snippet("plain-pdf", "response-file.adoc") shouldBe
                    """
                    [cols="1h,3"]
                    |===
                    |Media type
                    |`+application/pdf+`

                    |File name (example)
                    |`+manual.pdf+`

                    |Size (example)
                    |6 bytes

                    |===
                    """.trimIndent() + "\n"
            }

            then("response-body 는 언어 없는 [source] 블록이고, http-response 는 그대로다") {
                val response = docs.snippet("plain-pdf", "http-response.adoc")

                docs.snippet("plain-pdf", "response-body.adoc") shouldStartWithLine "[source,options=\"nowrap\"]"
                response shouldStartWithLine "[source,http,options=\"nowrap\"]"
                response shouldContain "Content-Type: application/pdf;charset=UTF-8"
            }

            then("요청 본문이 없으면 request-file 은 없고 openapi-extras 에 responseFile 도 없다") {
                docs.exists("plain-pdf", "request-file.adoc") shouldBe false
                docs.extras("plain-pdf").containsKey("responseFile") shouldBe false
            }
        }

        given("responseFile 로 설명하면") {
            val docs = FileDocs()

            docs.run("described-pdf", get("/manual")) { responseFile("제품 설명서 | PDF") }

            then("설명이 첫 행이고 표 칸 구분자는 이스케이프한다") {
                val table = docs.snippet("described-pdf", "response-file.adoc")

                table shouldContain "|Description\n|제품 설명서 \\| PDF\n\n|Media type"
            }

            then("openapi-extras.json 에 responseFile 설명이 남는다") {
                docs.extras("described-pdf")["responseFile"] shouldBe mapOf("description" to "제품 설명서 | PDF")
            }
        }

        given("글자 응답") {
            then("JSON 응답은 표를 만들지 않는다") {
                val docs = FileDocs()

                docs.run("json", get("/item"))

                docs.exists("json", "response-file.adoc") shouldBe false
                docs.snippet("json", "response-body.adoc") shouldStartWithLine "[source,json,options=\"nowrap\"]"
            }

            then("CSV 내보내기는 responseFile 로 설명하면 표가 생기고 본문 원문도 그대로다") {
                val docs = FileDocs()

                docs.run("csv", get("/export.csv")) { responseFile("상품 목록 CSV") }

                val table = docs.snippet("csv", "response-file.adoc")

                table shouldContain "|`+text/csv+`"
                table shouldContain "|19 bytes"
                table shouldNotContain "File name"
                docs.snippet("csv", "response-body.adoc") shouldStartWithLine "[source,csv,options=\"nowrap\"]"
            }
        }

        given("바이너리 요청 본문을 requestFile 로 설명하면") {
            val docs = FileDocs()
            val upload = put("/image").contentType(MediaType.IMAGE_PNG)
                .content(png)

            docs.run("upload-image", upload) { requestFile("업로드할 이미지 원본") }

            then("request-file 표와 언어 없는 request-body 가 생기고, 글자 응답에는 표가 없다") {
                val table = docs.snippet("upload-image", "request-file.adoc")

                table shouldContain "|Description\n|업로드할 이미지 원본"
                table shouldContain "|`+image/png+`"
                table shouldContain "|8 bytes"
                docs.snippet("upload-image", "request-body.adoc") shouldStartWithLine "[source,options=\"nowrap\"]"
                docs.exists("upload-image", "response-file.adoc") shouldBe false
            }

            then("openapi-extras.json 에 requestFile 설명이 남는다") {
                docs.extras("upload-image")["requestFile"] shouldBe mapOf("description" to "업로드할 이미지 원본")
            }
        }

        given("언어와 형식") {
            then("language = KO 이면 한국어 머리글과 크기다") {
                val docs = FileDocs()

                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    docs.run("korean", get("/one-byte")) { responseFile("원본 바이트") }
                }

                docs.snippet("korean", "response-file.adoc") shouldBe
                    """
                    [cols="1h,3"]
                    |===
                    |설명
                    |원본 바이트

                    |미디어 타입
                    |`+application/octet-stream+`

                    |크기 (예시)
                    |1바이트

                    |===
                    """.trimIndent() + "\n"
            }

            then("영어 1바이트는 단수형이다") {
                val docs = FileDocs()

                docs.run("one-byte", get("/one-byte"))

                docs.snippet("one-byte", "response-file.adoc") shouldContain "|1 byte\n"
            }

            then("Markdown 형식은 목록이다") {
                val docs = FileDocs(markdown = true)

                docs.run("markdown", get("/manual")) { responseFile("설명서") }

                docs.snippet("markdown", "response-file.md") shouldBe
                    """
                    - **Description**: 설명서
                    - **Media type**: `application/pdf`
                    - **File name (example)**: `manual.pdf`
                    - **Size (example)**: 6 bytes
                    """.trimIndent() + "\n"
            }
        }

        given("생성 문서에 넣을 스니펫") {
            val docs = FileDocs()

            docs.run("document", get("/manual")) { responseFile("설명서") }

            then("파일 표는 기본으로 들어가고, response-body 를 켜도 표가 그 자리를 대신한다") {
                val extractor = SnippetFileListExtractor(docs.output.absolutePath)
                val names = RestDocsConfiguration.withSettings({
                    document.snippets = SnippetFile.defaults() + SnippetFile.RESPONSE_BODY
                }) {
                    extractor.extract("file-body-documentation-test/document")
                        .map { it.fileName }
                }

                names shouldBe listOf(
                    "api-summary.adoc",
                    "curl-request.adoc",
                    "http-request.adoc",
                    "http-response.adoc",
                    "response-file.adoc",
                )
            }

            then("표를 빼면 response-body 가 그대로 들어간다") {
                val extractor = SnippetFileListExtractor(docs.output.absolutePath)
                val names = RestDocsConfiguration.withSettings({
                    document.snippets = SnippetFile.defaults() - SnippetFile.RESPONSE_FILE + SnippetFile.RESPONSE_BODY
                }) {
                    extractor.extract("file-body-documentation-test/document")
                        .map { it.fileName }
                }

                names.last() shouldBe "response-body.adoc"
            }
        }
    })

private infix fun String.shouldStartWithLine(line: String) {
    lineSequence().first() shouldBe line
}
