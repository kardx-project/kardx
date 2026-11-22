package com.example.restdocs.extraction

import io.github.kardx.annotation.Description
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.headerMeans
import io.github.kardx.extractor.wrappedResponseFields
import io.github.kardx.extractor.wrappedResponseFieldsOf
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andReturnAsObject
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.restDocsMockMvc
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Paths

data class RestResponse<T>(
    @Description("응답 코드") val code: Int,
    @Description("응답 메시지") val message: String,
    @Description("결과") val data: T?,
)

data class PageResponse<T>(@Description("전체 개수") val total: Long, @Description("목록") val content: List<T>)

@RestController
@RequestMapping("/wrapped")
class WrappedController {
    @GetMapping("/one")
    fun one() = RestResponse(200, "OK", Box(1, 2))

    @GetMapping("/many")
    fun many() = RestResponse(200, "OK", listOf(Box(1, 2)))

    @GetMapping("/page")
    fun page() = RestResponse(200, "OK", PageResponse(1, listOf(Box(1, 2))))

    @GetMapping("/empty")
    fun empty() = RestResponse<Box>(404, "Not Found", null)
}

@ExtendWith(RestDocumentationExtension::class)
@SpringBootTest
@DisplayName("래퍼와 보안")
class WrapperAndSecurityTest {

    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        // 계약 테스트는 문서가 아니므로 OpenAPI 스펙에 넣지 않는다.
        RestDocsConfiguration.configure {
            openApi.enabled = false
            extraction.objectMapper = { jsonMapper }
            extraction.responseWrapper = ResponseWrapper(RestResponse::class, dataPath = "data")
            masking.maskSensitive()
        }

        mockMvc = restDocsMockMvc(context, provider)
    }

    @AfterEach
    fun tearDown() = RestDocsConfiguration.reset()

    @Test
    @DisplayName("래퍼 + 객체")
    fun wrappedObject() {
        mockMvc.perform(get("/wrapped/one"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                wrappedResponseFields<Box>()
            }
    }

    @Test
    @DisplayName("래퍼 + 배열")
    fun wrappedArray() {
        mockMvc.perform(get("/wrapped/many"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                wrappedResponseFields<Box>("data[]")
            }
    }

    @Test
    @DisplayName("래퍼 + 페이지")
    fun wrappedPage() {
        mockMvc.perform(get("/wrapped/page"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                responseFields(wrappedResponseFieldsOf(PageResponse::class, Box::class, "data.content[]"))
            }
    }

    @Test
    @DisplayName("데이터 없는 응답")
    fun wrappedNull() {
        mockMvc.perform(get("/wrapped/empty"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                wrappedResponseFields<Box>(dataOptional = true)
            }
    }

    @Test
    @DisplayName("민감 헤더 마스킹과 API 요약")
    fun maskedHeader() {
        mockMvc.perform(
            get("/wrapped/one").header("Authorization", "Bearer secret-token"),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                description = "토큰은 문서에 남지 않는다."
                requestHeaders("Authorization" headerMeans "Bearer 토큰")
                wrappedResponseFields<Box>()
            }

        val dir = Paths.get("build/generated-snippets/wrapper-and-security-test/masked-header")
        val request =
            Files.readString(dir.resolve("http-request.adoc")) + Files.readString(dir.resolve("curl-request.adoc"))

        check("secret-token" !in request) { "token leaked: $request" }
        check("Authorization: Bearer ****" in request) { request }
        val summary = Files.readString(dir.resolve("api-summary.adoc"))
        check("`GET /wrapped/one`" in summary && "토큰은 문서에 남지 않는다." in summary) { summary }
    }

    @Test
    @DisplayName("제네릭 역직렬화")
    fun genericDeserialization() {
        val response =
            mockMvc.perform(get("/wrapped/many"))
                .andReturnAsObject<RestResponse<List<Box>>>()

        check(response.data == listOf(Box(1, 2))) { "$response" }
    }
}
