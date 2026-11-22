package io.github.kardx.dsl

import io.github.kardx.extractor.ofType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.restdocs.payload.JsonFieldType
import kotlin.reflect.typeOf

enum class Color { RED, BLUE }

/** OpenAPI·AsyncAPI 로 나가는 수식어(`ofType`, `constraint`, `example`, `withDefaultValue`, `DATE`, `enumOf`)가 남기는 속성. */
class SchemaModifierTest :
    BehaviorSpec({
        given("파라미터 ofType") {
            then("Kotlin 타입을 스키마 조각으로 남긴다") {
                ("id" pathMeans "ID" ofType Long::class).descriptor.attributes[SCHEMA_ATTRIBUTE] shouldBe
                    mapOf("type" to "integer", "format" to "int64")
                ("ids" queryMeans "ID 목록" ofType typeOf<List<Long>>()).descriptor.attributes[SCHEMA_ATTRIBUTE] shouldBe
                    mapOf("type" to "array", "items" to mapOf("type" to "integer", "format" to "int64"))
            }

            then("Enum 이면 값 목록도 넣는다") {
                val color = ("color" queryMeans "색" ofType Color::class).descriptor

                color.attributes[SCHEMA_ATTRIBUTE] shouldBe mapOf("type" to "string")
                color.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("RED", "BLUE")
            }
        }

        given("constraint") {
            then("표 문구와 OpenAPI·AsyncAPI 설명용 문구를 함께 남긴다") {
                val field = ("name" type STRING means "이름" constraint "2~10자").descriptor
                val param = ("q" queryMeans "검색어" constraint "2자 이상").descriptor
                val header = ("X-Id" headerMeans "추적 ID" constraint "UUID").descriptor

                field.attributes[CONSTRAINT_NOTE_ATTRIBUTE] shouldBe "2~10자"
                param.attributes[CONSTRAINT_NOTE_ATTRIBUTE] shouldBe "2자 이상"
                header.attributes[CONSTRAINT_NOTE_ATTRIBUTE] shouldBe "UUID"
                header.attributes[CONSTRAINTS_ATTRIBUTE] shouldBe "UUID"
            }
        }

        given("헤더 example·withDefaultValue") {
            then("표와 OpenAPI 가 읽는 example·default 속성을 남긴다") {
                val header = ("Accept-Language" headerMeans "응답 언어" withDefaultValue "ko" example "en").descriptor

                header.attributes[DEFAULT_VALUE_ATTRIBUTE] shouldBe "ko"
                header.attributes[EXAMPLE_ATTRIBUTE] shouldBe "en"
            }
        }

        given("날짜 타입") {
            then("DATE·DATETIME 은 문자열 + format") {
                ("d" type DATE means "날짜").descriptor.attributes[SCHEMA_ATTRIBUTE] shouldBe
                    mapOf("type" to "string", "format" to "date")
                ("t" type DATETIME means "일시").descriptor.attributes[SCHEMA_ATTRIBUTE] shouldBe
                    mapOf("type" to "string", "format" to "date-time")
                ("s" type STRING means "문자열").descriptor.attributes[SCHEMA_ATTRIBUTE] shouldBe null
            }
        }

        given("enumOf") {
            then("필드 타입은 바꾸지 않는다 (표는 String, REST Docs 가 문자열을 검증)") {
                val field = ("color" type ENUM means "색" enumOf Color::class).descriptor

                field.type shouldBe JsonFieldType.STRING
                field.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("RED", "BLUE")
            }
        }
    })
