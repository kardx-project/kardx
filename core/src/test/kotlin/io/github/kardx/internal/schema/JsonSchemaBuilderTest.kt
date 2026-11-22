package io.github.kardx.internal.schema

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private fun field(
    path: String,
    type: String,
    schema: Map<String, Any?>? = null,
    optional: Boolean = false,
    note: String? = null,
    default: String? = null,
    example: String? = null,
    requiredByConstraint: Boolean = false,
) = SchemaField(
    path = path,
    type = type,
    description = null,
    optional = optional,
    ignored = false,
    constraints = note,
    enumValues = null,
    schema = schema,
    constraintNote = note,
    defaultValue = default,
    example = example,
    requiredByConstraint = requiredByConstraint,
)

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.property(name: String): Map<String, Any?> =
    (this["properties"] as Map<String, Map<String, Any?>>).getValue(name)

class JsonSchemaBuilderTest :
    BehaviorSpec({
        given("스키마 조각이 있는 필드") {
            val fields = listOf(
                field("id", "Number", mapOf("type" to "integer", "format" to "int64")),
                field("score", "Number", mapOf("type" to "integer", "minimum" to 0, "exclusiveMinimum" to true)),
                field(
                    "tags",
                    "Array",
                    mapOf("type" to "array", "items" to mapOf("type" to "string", "minLength" to 1)),
                ),
                field("name", "String", mapOf("type" to "integer")),
                field("size", "Number", mapOf("type" to "integer"), optional = true, default = "20", example = "10"),
                field("memo", "String", optional = true, note = "Past - 과거", requiredByConstraint = true),
            )

            then("JSON Schema(AsyncAPI) 는 exclusiveMinimum 을 숫자로, 예시는 examples 로") {
                val schema = JsonSchemaBuilder.fromFields(fields)

                schema.property("id") shouldBe mapOf("type" to "integer", "format" to "int64")
                schema.property("score") shouldBe mapOf("type" to "integer", "exclusiveMinimum" to 0)
                schema.property("tags")["items"] shouldBe mapOf("type" to "string", "minLength" to 1)
                schema.property("size")["default"] shouldBe 20L
                schema.property("size")["examples"] shouldBe listOf(10L)
                schema["required"] shouldBe listOf("id", "score", "tags", "name", "memo")
            }

            then("타입이 맞지 않는 조각은 버린다 (직접 기술한 타입이 우선)") {
                JsonSchemaBuilder.fromFields(fields).property("name") shouldBe mapOf("type" to "string")
            }

            then("OpenAPI 3.0 은 exclusiveMinimum 불리언, 예시는 example") {
                val schema = JsonSchemaBuilder.fromFields(fields, SchemaDialect.OPENAPI_30)

                schema.property("score") shouldBe
                    mapOf("type" to "integer", "minimum" to 0, "exclusiveMinimum" to true)
                schema.property("size")["example"] shouldBe 10L
            }

            then("키워드로 옮기지 못한 제약은 설명 뒤에 붙인다") {
                JsonSchemaBuilder.fromFields(fields).property("memo")["description"] shouldBe "Constraints: Past - 과거"
            }

            then("language 가 ko 면 접두사는 `제약:` 이다") {
                val schema = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    JsonSchemaBuilder.fromFields(fields)
                }

                schema.property("memo")["description"] shouldBe "제약: Past - 과거"
            }
        }

        given("null 일 수 있는 필드") {
            val fields = listOf(
                field("memo", "String", optional = true).copy(nullable = true),
                field("note", "String", optional = true, requiredByConstraint = true).copy(nullable = true),
                field("tags", "Array", optional = true).copy(nullableItems = true),
                field("tags[]", "String"),
            )

            then("OpenAPI 3.0 은 Gradle 플러그인처럼 nullable 을 넣는다 (꼭 있어야 하는 필드는 빼고, 원소는 items 에)") {
                val schema = JsonSchemaBuilder.fromFields(fields, SchemaDialect.OPENAPI_30)

                schema.property("memo")["nullable"] shouldBe true
                schema.property("note").containsKey("nullable") shouldBe false
                schema.property("tags").containsKey("nullable") shouldBe false
                schema.property("tags")["items"] shouldBe mapOf("type" to "string", "nullable" to true)
            }

            then("JSON Schema(AsyncAPI) 에는 넣지 않는다") {
                val schema = JsonSchemaBuilder.fromFields(fields)

                schema.property("memo").containsKey("nullable") shouldBe false
                schema.property("tags")["items"] shouldBe mapOf("type" to "string")
            }
        }

        given("기본값·예시 문자열") {
            then("스키마 타입에 맞춰 바꾸고, 바꿀 수 없으면 그대로 둔다") {
                SchemaHints.coerce("1.5", mapOf("type" to "number")) shouldBe BigDecimal("1.5")
                SchemaHints.coerce("true", mapOf("type" to "boolean")) shouldBe true
                SchemaHints.coerce("a, b", mapOf("type" to "array", "items" to mapOf("type" to "string"))) shouldBe
                    listOf("a", "b")
                SchemaHints.coerce("many", mapOf("type" to "integer")) shouldBe "many"
            }
        }
    })
