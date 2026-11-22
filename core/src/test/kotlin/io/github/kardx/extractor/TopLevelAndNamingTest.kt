package io.github.kardx.extractor
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import io.github.kardx.config.ObjectMapperProvider
import io.github.kardx.config.PropertyNameStrategy
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.ENUM
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.queryMeans
import io.github.kardx.dsl.type
import io.github.kardx.internal.openapi.OpenApiDescriptors
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.SubsectionDescriptor
import kotlin.reflect.typeOf

data class Item(val itemId: Long, val itemName: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SnakeItem(val itemId: Long, @param:JsonProperty("NAME") val itemName: String, val detail: Item)

enum class Grade2 { GOLD, SILVER }

data class WithGrade(val grade: Grade2)

data class CamelHolder(val ownerName: String, val firstItem: Item)

/** Jackson 3 의 `NamingBase` 처럼 `translate` 는 protected 이고 `nameForField` 만 public 인 전략. */
open class ProtectedTranslateStrategy {
    protected fun translate(name: String): String = name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() }

    // Jackson 의 NamingBase 처럼 config·field 는 보지 않는다
    @Suppress("UNUSED_PARAMETER")
    fun nameForField(config: Any?, field: Any?, defaultName: String): String = translate(defaultName)
}

/** Jackson 3 `JsonMapper` 처럼 `serializationConfig()` 로 설정을 꺼내는 매퍼. */
class Jackson3LikeMapper {
    fun serializationConfig(): Jackson3LikeConfig = Jackson3LikeConfig()
}

class Jackson3LikeConfig {
    fun getPropertyNamingStrategy(): ProtectedTranslateStrategy = ProtectedTranslateStrategy()
}

private inline fun <reified T> paths(prefix: String = "") = responseFieldsOf<T>(prefix)
    .map { it.descriptor.path }

class TopLevelAndNamingTest :
    BehaviorSpec({
        given("최상위가 객체가 아닌 타입") {
            then("컬렉션은 원소 필드를 [] 아래에 만든다") {
                paths<List<Item>>() shouldContainExactly listOf("[].itemId", "[].itemName")
                paths<Array<Item>>() shouldContainExactly listOf("[].itemId", "[].itemName")
            }

            then("접두사가 있으면 배열 줄과 원소 필드를 만든다") {
                val fields =
                    responseFieldsOf<List<Item>>("data")
                        .map { it.descriptor }

                fields.map { it.path } shouldContainExactly listOf("data", "data[].itemId", "data[].itemName")
                fields.first().type shouldBe JsonFieldType.ARRAY
            }

            then("String, Map, 값 컬렉션은 접두사 없이는 문서화할 필드가 없다") {
                paths<String>().shouldBeEmpty()
                paths<Map<String, Item>>().shouldBeEmpty()
                paths<List<String>>().shouldBeEmpty()
                paths<Item?>() shouldContainExactly listOf("itemId", "itemName")
            }

            then("접두사가 있으면 값은 한 줄, Map 은 subsection 한 줄") {
                paths<String>("message") shouldContainExactly listOf("message")

                val map = responseFieldsOf<Map<String, Item>>("data").single().descriptor

                map.path shouldBe "data"
                map.shouldBeInstanceOf<SubsectionDescriptor>()
            }

            then("원소가 Map 인 최상위 배열은 [] subsection 이다") {
                responseFieldsOf<List<Map<String, Int>>>().single().descriptor.path shouldBe "[]"
            }

            then("파라미터는 접두사가 있을 때만 이름 하나") {
                parametersOf(typeOf<List<String>>()).shouldBeEmpty()
                parametersOf(typeOf<List<String>>(), "ids")
                    .map { it.descriptor.name } shouldContainExactly listOf("ids")
            }
        }

        given("Jackson 이름 규칙") {
            then("클래스의 @JsonNaming 을 따르고 @JsonProperty 이름은 그대로 둔다") {
                paths<SnakeItem>() shouldContainExactly listOf(
                    "item_id",
                    "NAME",
                    "detail",
                    "detail.itemId",
                    "detail.itemName",
                )
            }

            then("매퍼의 전역 전략을 하위 객체까지 적용한다") {
                val mapper =
                    ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

                RestDocsConfiguration.configure { extraction.objectMapper = ObjectMapperProvider { mapper } }

                paths<CamelHolder>() shouldContainExactly listOf(
                    "owner_name",
                    "first_item",
                    "first_item.item_id",
                    "first_item.item_name",
                )
            }

            then("extraction.propertyNameStrategy 설정이 매퍼보다 우선한다") {
                RestDocsConfiguration.configure {
                    extraction.objectMapper = ObjectMapperProvider {
                        ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    }

                    extraction.propertyNameStrategy =
                        PropertyNameStrategy { PropertyNamingStrategies.KebabCaseStrategy().translate(it.name) }
                }

                paths<Item>() shouldContainExactly listOf("item-id", "item-name")
            }

            then("translate 가 protected 인 Jackson 3 전략도 nameForField 로 적용한다") {
                RestDocsConfiguration.configure {
                    extraction.objectMapper =
                        ObjectMapperProvider { Jackson3LikeMapper() }
                }

                paths<CamelHolder>() shouldContainExactly listOf(
                    "owner_name",
                    "first_item",
                    "first_item.item_id",
                    "first_item.item_name",
                )
            }

            then("파라미터는 Jackson 을 거치지 않으므로 전역·클래스 이름 규칙을 적용하지 않는다") {
                RestDocsConfiguration.configure {
                    extraction.objectMapper =
                        ObjectMapperProvider {
                            ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        }
                }

                parametersOf<CamelHolder>().map { it.descriptor.name } shouldContainExactly
                    listOf("ownerName", "firstItem.itemId", "firstItem.itemName")
                parametersOf<SnakeItem>().map { it.descriptor.name } shouldContainExactly
                    listOf("itemId", "itemName", "detail.itemId", "detail.itemName")
            }

            then("전략이 없으면 프로퍼티 이름 그대로") {
                paths<Item>() shouldContainExactly listOf("itemId", "itemName")
            }
        }

        given("Enum 필드와 파라미터") {
            then("상수 목록을 OpenAPI enum 용 속성으로 넣는다") {
                val field =
                    responseFieldsOf<WithGrade>()
                        .single { it.descriptor.path == "grade" }
                        .descriptor

                // REST Docs 는 문자열로 검증하고 표에도 String 으로 나간다. epages 에 넘기는 복사본만 enum 이다
                field.type shouldBe JsonFieldType.STRING
                OpenApiDescriptors.field(field).type shouldBe "enum"
                field.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("GOLD", "SILVER")
                parametersOf<WithGrade>().single().descriptor.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe
                    listOf("GOLD", "SILVER")
            }

            then("직접 쓸 때는 enumOf") {
                val field = ("grade" type ENUM means "등급" enumOf Grade2::class).descriptor

                field.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("GOLD", "SILVER")
                ("grade" queryMeans "등급" enumOf Grade2::class).descriptor.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe
                    listOf("GOLD", "SILVER")
            }
        }
    })
