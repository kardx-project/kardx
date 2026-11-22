package io.github.kardx.support
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.ObjectMapperProvider
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.mockmvc.andDocument
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Files

/** [payload] 를 그대로 응답하는 컨트롤러. */
@RestController
class PayloadController(private val payload: Any) {
    @GetMapping("/payload")
    fun payload(): Any = payload
}

/**
 * [payload] 를 [mapper] 로 직렬화해 응답하고, [fields] 로 Spring REST Docs 응답 필드 검증을 돌린다.
 * 추출 결과가 실제 JSON 과 다르면 `SnippetException` 으로 실패한다. 추출과 직렬화가 같은 매퍼를 쓴다.
 */
fun verifyResponseFields(payload: Any, mapper: ObjectMapper = ObjectMapper(), fields: () -> List<RestDocsField>) {
    val output = Files.createTempDirectory("payload-contract").toFile()
    val documentation = ManualRestDocumentation(output.absolutePath)
    val mockMvc = MockMvcBuilders.standaloneSetup(PayloadController(payload))
        .setMessageConverters(MappingJackson2HttpMessageConverter(mapper))
        .apply<StandaloneMockMvcBuilder>(documentationConfiguration(documentation))
        .build()

    documentation.beforeTest(PayloadController::class.java, "payload")

    try {
        RestDocsConfiguration.withSettings({ extraction.objectMapper = ObjectMapperProvider { mapper } }) {
            mockMvc.perform(get("/payload")).andDocument { responseFields(fields()) }
        }
    } finally {
        documentation.afterTest()
    }
}
