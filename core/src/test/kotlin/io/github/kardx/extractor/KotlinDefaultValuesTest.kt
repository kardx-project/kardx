package io.github.kardx.extractor

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.kardx.annotation.Masked
import io.github.kardx.dsl.DEFAULT_VALUE_ATTRIBUTE
import io.github.kardx.internal.openapi.fieldDefaults
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

enum class DefaultSort {
    @JsonProperty("newest")
    NEWEST,
    OLDEST,
}

data class PageQuery(
    val keyword: String,
    val page: Int = 0,
    val size: Int = 20,
    val sort: DefaultSort = DefaultSort.NEWEST,
    val exact: Boolean = false,
    val minPrice: BigDecimal = BigDecimal("9.90"),
    val owner: String? = null,
    val tags: List<String> = listOf("a"),
    val label: String = "",
)

data class PagedSearch(val filter: SearchFilter, val page: PageInfo = PageInfo())

data class SearchFilter(val status: String = "ACTIVE")

data class PageInfo(val size: Int = 50)

/** 기본값이 없는 객체 파라미터. 자리 객체를 만들어 넣는다 */
data class WithRequiredObject(val filter: SearchFilter, val size: Int = 10)

sealed interface DefaultsMethod

data class DefaultsCard(val number: String, val months: Int = 0) : DefaultsMethod

/** 기본값이 없는 sealed 파라미터. 첫 하위 타입의 자리 객체를 넣는다 */
data class WithSealedObject(val method: DefaultsMethod, val currency: String = "KRW")

/** sealed 가 아닌 인터페이스 파라미터. 자리 값을 정할 수 없어 기본값을 모른다 */
data class WithInterface(val value: Comparable<String>, val size: Int = 10)

/** 자기 자신을 필수 파라미터로 받는다. 깊이 한도에서 멈추고 기본값을 모른다 */
data class SelfReferencing(val parent: SelfReferencing, val size: Int = 10)

/** 실행마다 달라지는 기본값과 가리는 값의 기본값 */
data class VolatileDefaults(
    val traceId: String = UUID.randomUUID().toString(),
    val at: Long = System.nanoTime(),
    @Masked val apiKey: String = "secret-key",
    val size: Int = 10,
)

/** 자리 값으로는 init 이 실패한다 */
data class WithValidation(val name: String, val limit: Int = 5) {
    init {
        require(name.isNotEmpty())
    }
}

/** 다른 파라미터에서 계산하는 기본값. 자리 값에 따라 달라지므로 붙이지 않는다 */
data class DerivedDefaults(
    val limit: Int,
    val pageSize: Int = limit,
    val label: String = "page-$limit",
    val owner: String?,
    val ownerLabel: String = owner ?: "anonymous",
    val strict: Boolean,
    val mode: String = if (strict) "strict" else "lenient",
    val fixed: Int = 20,
)

/** init 의 부수 효과. 기본값을 읽으려고 객체를 만든 횟수를 센다 */
class SideEffectCounter(val name: String, val size: Int = 10) {
    init {
        created++
    }

    companion object {
        var created: Int = 0
    }
}

class KotlinDefaultValuesTest :
    BehaviorSpec({
        given("기본값이 있는 생성자 파라미터의 쿼리 파라미터 (parametersOf)") {
            val params = parametersOf<PageQuery>().associate { it.name to it.descriptor.attributes }

            then("값 타입의 기본값을 JSON 표현으로 붙인다") {
                params.getValue("page") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "0")
                params.getValue("size") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "20")
                params.getValue("sort") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "newest")
                params.getValue("exact") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "false")
                params.getValue("minPrice") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "9.90")
            }

            then("기본값이 없거나 null·컬렉션·빈 문자열 기본값은 붙이지 않는다") {
                params.getValue("keyword") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                params.getValue("owner") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                params.getValue("tags") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                params.getValue("label") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }

            then("직접 적은 withDefaultValue 가 이긴다") {
                val size = parametersOf<PageQuery>().first { it.name == "size" } withDefaultValue "30"

                size.descriptor.attributes shouldContain (DEFAULT_VALUE_ATTRIBUTE to "30")
            }
        }

        given("중첩 객체의 기본값 (요청 본문)") {
            val fields = requestFieldsOf<PagedSearch>()
            val attributes = fields.associate { it.descriptor.path to it.descriptor.attributes }

            then("하위 객체 클래스의 기본값을 그 경로에 붙인다") {
                attributes.getValue("filter.status") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "ACTIVE")
                attributes.getValue("page.size") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "50")
            }

            then("fieldDefaults 는 스키마 타입의 값이다") {
                fieldDefaults(fields.map { it.descriptor }, emptyList()) shouldBe
                    mapOf("requestBody" to mapOf("filter.status" to "ACTIVE", "page.size" to 50L))
            }
        }

        given("응답 본문") {
            val attributes = responseFieldsOf<PageQuery>().associate { it.descriptor.path to it.descriptor.attributes }

            then("기본값을 읽지 않는다 (응답에는 항상 값이 있다)") {
                attributes.getValue("size") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }
        }

        given("기본값이 없는 객체 파라미터") {
            then("Kotlin 클래스는 자리 객체를 넣고 기본값을 읽는다") {
                parametersOf<WithRequiredObject>().first { it.name == "size" }
                    .descriptor
                    .attributes shouldContain (DEFAULT_VALUE_ATTRIBUTE to "10")
            }

            then("sealed 타입은 첫 하위 타입의 자리 객체를 넣는다") {
                requestFieldsOf<WithSealedObject>().first { it.descriptor.path == "currency" }
                    .descriptor
                    .attributes shouldContain (DEFAULT_VALUE_ATTRIBUTE to "KRW")
            }
        }

        given("객체를 만들 수 없는 클래스") {
            then("자리 값을 정할 수 없는 파라미터가 있으면 기본값을 모른다") {
                parametersOf<WithInterface>().first { it.name == "size" }
                    .descriptor
                    .attributes shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }

            then("자기 자신을 필수로 받으면 깊이 한도에서 멈추고 기본값을 모른다") {
                parametersOf<SelfReferencing>().first { it.name == "size" }
                    .descriptor
                    .attributes shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }

            then("init 이 실패하면 기본값을 모른다") {
                parametersOf<WithValidation>().first { it.name == "limit" }
                    .descriptor
                    .attributes shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }
        }

        given("다른 파라미터에서 계산하는 기본값") {
            val params = parametersOf<DerivedDefaults>().associate { it.name to it.descriptor.attributes }

            then("필수 파라미터의 자리 값을 바꿔 두 번 만들어, 값이 달라지는 기본값은 붙이지 않는다") {
                params.getValue("pageSize") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                params.getValue("label") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                params.getValue("mode") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                params.getValue("fixed") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "20")
            }

            then("nullable 파라미터에서 계산하는 기본값도 붙이지 않는다") {
                params.getValue("ownerLabel") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }

            then("클래스마다 두 번만 만든다") {
                SideEffectCounter.created = 0

                repeat(3) { parametersOf<SideEffectCounter>() }

                SideEffectCounter.created shouldBe 2
            }
        }

        given("실행마다 달라지는 기본값") {
            val attributes = requestFieldsOf<VolatileDefaults>().associate {
                it.descriptor.path to
                    it.descriptor.attributes
            }

            then("두 번 만들어 값이 다르면 붙이지 않는다") {
                attributes.getValue("traceId") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                attributes.getValue("at") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
                attributes.getValue("size") shouldContain (DEFAULT_VALUE_ATTRIBUTE to "10")
            }

            then("가리는 값(@Masked)의 기본값은 붙이지 않는다") {
                attributes.getValue("apiKey") shouldNotContainKey DEFAULT_VALUE_ATTRIBUTE
            }
        }
    })
