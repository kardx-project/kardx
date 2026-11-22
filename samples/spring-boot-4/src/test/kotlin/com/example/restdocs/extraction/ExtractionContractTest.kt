package com.example.restdocs.extraction

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kardx.annotation.Description
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.extractor.queryParameters
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andReturnAsObject
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import io.github.kardx.mockmvc.restDocsMockMvc
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.web.PagedModel
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.domain.Page as DataPage

data class Box(@Description("길이") val length: Int, @Description("폭") val width: Int)

data class Address(@Description("도로명") val street: String, @Description("우편번호") val zip: String)

data class Category(@Description("이름") val name: String, @Description("하위 카테고리") val children: List<Category>)

@JvmInline
value class OrderId(val value: Long)

sealed interface Payment {
    val amount: Long
}

data class Card(override val amount: Long, val cardNo: String) : Payment

data class Bank(override val amount: Long, val account: String) : Payment

data class Page<T>(val content: List<T>, val total: Long)

data class ProbeResponse(
    @Description("주문 ID") val id: OrderId,
    @Description("금액") val price: BigDecimal,
    @Description("고유값") val uid: UUID,
    @Description("날짜") val dates: List<LocalDate>,
    @Description("태그") val tags: List<String>,
    @Description("박스") val boxes: List<Box>,
    @Description("행렬") val matrix: List<List<Box>>,
    @Description("배송지") val address: Address?,
    @Description("메타데이터") val metadata: Map<String, Any>,
    @Description("카테고리") val category: Category,
    @Description("결제") val payment: Payment,
    @get:JsonProperty("display_name") @Description("표시 이름") val displayName: String,
    @JsonIgnore val secret: String = "hidden",
)

data class ProbeRequest(
    @Description("이름") @field:NotBlank val name: String,
    @Description("배송지") val address: Address?,
    @Description("박스") val boxes: List<Box>,
)

data class EmptyArrays(@Description("박스") val boxes: List<Box>)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SnakeProfile(@Description("이름") val firstName: String, @Description("성") val lastName: String)

object ContractMarker {
    val version: Int get() = 1
}

interface Pet {
    val name: String
}

data class Dog(override val name: String, val age: Int) : Pet

enum class Level { LOW, HIGH }

/** 프로퍼티 있는 object, 인터페이스 타입, Any, Enum 목록, null 원소가 있는 목록. */
data class Shapes(
    val marker: ContractMarker,
    val pet: Pet,
    val anything: Any,
    val levels: List<Level>,
    val boxes: List<Box?>,
)

@RestController
@RequestMapping("/contract")
class ContractController {
    @GetMapping("/probe")
    fun probe() = ProbeResponse(
        id = OrderId(7),
        price = BigDecimal("1200.50"),
        uid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        dates = listOf(LocalDate.of(2026, 11, 5)),
        tags = listOf("a", "b"),
        boxes = listOf(Box(1, 2)),
        matrix = listOf(listOf(Box(3, 4))),
        address = null,
        metadata = mapOf("k" to mapOf("nested" to 1)),
        category = Category("root", listOf(Category("leaf", emptyList()))),
        payment = Card(1000, "1234-****"),
        displayName = "홍길동",
    )

    @GetMapping("/page")
    fun page() = Page(content = listOf(Box(1, 2)), total = 1)

    @GetMapping("/empty")
    fun empty() = EmptyArrays(emptyList())

    @GetMapping("/list")
    fun list() = listOf(Box(1, 2))

    @GetMapping("/snake")
    fun snake() = SnakeProfile("길동", "홍")

    @GetMapping("/shapes")
    fun shapes() = Shapes(
        marker = ContractMarker,
        pet = Dog("멍", 3),
        anything = mapOf("k" to mapOf("n" to 1)),
        levels = listOf(Level.HIGH),
        boxes = listOf(Box(1, 2), null),
    )

    @GetMapping("/data-page")
    fun dataPage(): DataPage<Box> = PageImpl(listOf(Box(1, 2)), PageRequest.of(0, 2), 5)

    @GetMapping("/slice")
    fun slice(): Slice<Box> = SliceImpl(listOf(Box(1, 2)), PageRequest.of(0, 2), true)

    @GetMapping("/paged-model")
    fun pagedModel(): PagedModel<Box> = PagedModel(PageImpl(listOf(Box(1, 2)), PageRequest.of(0, 2), 5))

    @GetMapping("/problem")
    fun problem(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "없는 주문")

    @PostMapping("/probe")
    fun echo(@RequestBody request: ProbeRequest) = request
}

/**
 * 추출 결과를 실제 Jackson 3 응답에 대고 Spring REST Docs 4 가 검증한다.
 * 경로가 틀리거나 문서화되지 않은 필드가 있으면 SnippetException 으로 실패한다.
 */
@ExtendWith(RestDocumentationExtension::class)
@SpringBootTest
@DisplayName("추출 계약")
class ExtractionContractTest {

    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jsonMapper: JsonMapper

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        // 계약 테스트는 문서가 아니므로 OpenAPI 스펙에 넣지 않는다.
        RestDocsConfiguration.configure {
            openApi.enabled = false
            extraction.objectMapper = { jsonMapper }
        }

        mockMvc = restDocsMockMvc(context, provider)
    }

    @AfterEach
    fun tearDown() = RestDocsConfiguration.reset()

    @Test
    @DisplayName("복합 응답")
    fun probe() {
        mockMvc.perform(get("/contract/probe"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                responseFields<ProbeResponse>()
            }
    }

    @Test
    @DisplayName("제네릭 응답")
    fun page() {
        mockMvc.perform(get("/contract/page"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                responseFields<Page<Box>>()
            }
    }

    @Test
    @DisplayName("빈 배열 응답")
    fun emptyArray() {
        // 이 테스트에서만 빈 배열 원소 필드를 optional 로 본다.
        RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) {
            mockMvc.perform(get("/contract/empty"))
                .andStatus(HttpStatus.OK)
                .andDocument { responseFields<EmptyArrays>() }
        }
    }

    @Test
    @DisplayName("응답 역직렬화와 jsonPath 검증")
    fun helpers() {
        val box =
            mockMvc.perform(get("/contract/page"))
                .andReturn()
                .let {
                    jsonMapper.readTree(it.response.contentAsString)
                }

        val page =
            mockMvc.perform(get("/contract/list"))
                .andReturnAsObject<Array<Box>>()

        check(page.single() == Box(1, 2))
        check(box.get("total").asLong() == 1L)

        mockMvc.perform(get("/contract/probe"))
            .andExpectAll(jsonPath("$.display_name").value("홍길동"), jsonPath("$.id").value(7))
    }

    @Test
    @DisplayName("최상위 배열 응답")
    fun list() {
        mockMvc.perform(get("/contract/list"))
            .andStatus(HttpStatus.OK)
            .andDocument {
                responseFields<List<Box>>()
            }
    }

    @Test
    @DisplayName("요청 본문")
    fun request() {
        mockMvc.perform(
            post("/contract/probe").jsonContent(ProbeRequest("홍길동", Address("강남대로", "06000"), listOf(Box(1, 2)))),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                requestFields<ProbeRequest>()
                responseFields<ProbeRequest>()
            }
    }

    @Test
    @DisplayName("@JsonNaming 이름 전략")
    fun snakeCase() {
        mockMvc.perform(get("/contract/snake"))
            .andStatus(HttpStatus.OK)
            .andDocument { responseFields<SnakeProfile>() }
    }

    @Test
    @DisplayName("object·인터페이스·Any·Enum 목록·null 원소")
    fun shapes() {
        mockMvc.perform(get("/contract/shapes"))
            .andStatus(HttpStatus.OK)
            .andDocument { responseFields<Shapes>() }
    }

    // 문서 이름이 테스트 메서드에서 나오므로 요청마다 메서드를 나눈다. 한 메서드에 모으면 마지막 요청의 문서만 남는다
    @Test
    @DisplayName("Spring Data Page")
    fun springDataPage() {
        mockMvc.perform(get("/contract/data-page"))
            .andDocument { responseFields<DataPage<Box>>() }
    }

    @Test
    @DisplayName("Spring Data Slice")
    fun springDataSlice() {
        mockMvc.perform(get("/contract/slice"))
            .andDocument { responseFields<Slice<Box>>() }
    }

    @Test
    @DisplayName("Spring Data PagedModel")
    fun springDataPagedModel() {
        mockMvc.perform(get("/contract/paged-model"))
            .andDocument { responseFields<PagedModel<Box>>() }
    }

    @Test
    @DisplayName("Pageable 쿼리 파라미터")
    fun pageable() {
        mockMvc.perform(
            get("/contract/list").param("page", "0")
                .param("size", "2")
                .param("sort", "id,desc"),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                queryParameters<Pageable>()
                responseFields<List<Box>>()
            }
    }

    @Test
    @DisplayName("ProblemDetail 오류 응답")
    fun problemDetail() {
        mockMvc.perform(get("/contract/problem"))
            .andStatus(HttpStatus.NOT_FOUND)
            .andDocument { responseFields<ProblemDetail>() }
    }
}
