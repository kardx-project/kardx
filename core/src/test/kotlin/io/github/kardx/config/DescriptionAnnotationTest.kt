package io.github.kardx.config

import com.fasterxml.jackson.annotation.JsonPropertyDescription
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.swagger.v3.oas.annotations.media.Schema

/** `value` 와 `description` 이 둘 다 있으면 `value` 를 읽는다. */
@Retention(AnnotationRetention.RUNTIME)
annotation class LabelDoc(val value: String = "", val description: String = "")

/** `description` 만 있다. */
@Retention(AnnotationRetention.RUNTIME)
annotation class OnlyDescriptionDoc(val description: String)

/** 이름이 정해지지 않은 속성. `#text` 로 지정해야 한다. */
@Retention(AnnotationRetention.RUNTIME)
annotation class TextDoc(val text: String, val order: Int = 0)

/** `value` 가 String 이 아니다. */
@Retention(AnnotationRetention.RUNTIME)
annotation class NumberDoc(val value: Int)

class NotAnAnnotation

class DescriptionAnnotationTest :
    BehaviorSpec({
        given("parse: 별칭") {
            then("swagger 는 @Schema 의 description, jackson 은 @JsonPropertyDescription 의 value 다") {
                DescriptionAnnotation.parse("swagger") shouldBe DescriptionAnnotation.of(Schema::class, "description")
                DescriptionAnnotation.parse("jackson") shouldBe
                    DescriptionAnnotation.of(JsonPropertyDescription::class, "value")
            }

            then("대소문자와 앞뒤 공백은 가리지 않는다") {
                DescriptionAnnotation.parse(" Swagger ") shouldBe DescriptionAnnotation.parse("swagger")
            }

            then("별칭은 가리키는 어노테이션의 FQCN#attribute 로 보인다") {
                DescriptionAnnotation.parse("swagger").toString() shouldBe
                    "io.swagger.v3.oas.annotations.media.Schema#description"
                DescriptionAnnotation.parse("jackson").toString() shouldBe
                    "com.fasterxml.jackson.annotation.JsonPropertyDescription#value"
            }
        }

        given("parse: 별칭#attribute") {
            then("별칭이 가리키는 어노테이션의 그 속성을 읽는다") {
                DescriptionAnnotation.parse("swagger#title") shouldBe DescriptionAnnotation.of(Schema::class, "title")
                DescriptionAnnotation.parse(" Swagger#title ") shouldBe DescriptionAnnotation.of(Schema::class, "title")
                DescriptionAnnotation.parse("jackson#value") shouldBe
                    DescriptionAnnotation.of(JsonPropertyDescription::class, "value")
            }

            then("# 앞뒤 공백은 무시한다") {
                DescriptionAnnotation.parse("swagger # title") shouldBe DescriptionAnnotation.of(Schema::class, "title")
                DescriptionAnnotation.parse("${TextDoc::class.java.name} #text") shouldBe
                    DescriptionAnnotation.of(TextDoc::class, "text")
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("swagger #  ") }
                    .message shouldBe "'swagger #' has no attribute name after '#'"
            }

            then("FQCN 으로 쓴 것과 같다") {
                DescriptionAnnotation.parse("swagger#title") shouldBe
                    DescriptionAnnotation.parse("${Schema::class.java.name}#title")
            }

            then("속성이 없으면 어노테이션 이름과 속성 이름을 알려 준다") {
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("swagger#nope") }
                    .message shouldBe "annotation '${Schema::class.java.name}' has no String attribute 'nope'"
            }

            then("# 뒤가 비면 알려 준다") {
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("swagger#") }
                    .message shouldBe "'swagger#' has no attribute name after '#'"
            }
        }

        given("parse: FQCN") {
            then("속성을 생략하면 value, 없으면 description 을 읽는다") {
                DescriptionAnnotation.parse(LabelDoc::class.java.name).attribute shouldBe "value"
                DescriptionAnnotation.parse(OnlyDescriptionDoc::class.java.name).attribute shouldBe "description"
                DescriptionAnnotation.parse(Schema::class.java.name).attribute shouldBe "description"
            }

            then("FQCN#attribute 는 그 속성을 읽는다") {
                val parsed = DescriptionAnnotation.parse("${TextDoc::class.java.name}#text")

                parsed shouldBe DescriptionAnnotation.of(TextDoc::class, "text")
                parsed.toString() shouldBe "${TextDoc::class.java.name}#text"
            }
        }

        given("잘못된 지정") {
            then("클래스가 없으면 클래스 이름을 알려 준다") {
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("com.nope.Missing") }
                    .message shouldBe "class 'com.nope.Missing' not found"
            }

            then("어노테이션이 아니면 알려 준다") {
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse(NotAnAnnotation::class.java.name) }
                    .message shouldBe "class '${NotAnAnnotation::class.java.name}' is not an annotation"
            }

            then("속성이 없거나 String 이 아니면 알려 준다") {
                shouldThrow<IllegalArgumentException> {
                    DescriptionAnnotation.parse("${TextDoc::class.java.name}#nope")
                }.message shouldBe "annotation '${TextDoc::class.java.name}' has no String attribute 'nope'"

                shouldThrow<IllegalArgumentException> {
                    DescriptionAnnotation.parse("${TextDoc::class.java.name}#order")
                }.message shouldContain "has no String attribute 'order'"

                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse(TextDoc::class.java.name) }
                    .message shouldContain "has no String attribute 'value' or 'description'"
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.of(NumberDoc::class) }
                    .message shouldContain "has no String attribute 'value' or 'description'"
            }

            then("어노테이션 이름이 비면 알려 준다") {
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("") }
                    .message shouldBe "'' has no annotation name"
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("#x") }
                    .message shouldBe "'#x' has no annotation name"
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("  #  ") }
                    .message shouldBe "'#' has no annotation name"
            }

            then("상속한 메서드(toString, hashCode, annotationType)는 속성이 아니다") {
                listOf("toString", "hashCode", "annotationType").forEach { name ->
                    shouldThrow<IllegalArgumentException> { DescriptionAnnotation.of(TextDoc::class, name) }
                        .message shouldBe "annotation '${TextDoc::class.java.name}' has no String attribute '$name'"
                }

                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("swagger#toString") }
                    .message shouldBe "annotation '${Schema::class.java.name}' has no String attribute 'toString'"
            }

            then("# 뒤에 속성 이름이 없으면 알려 준다") {
                shouldThrow<IllegalArgumentException> { DescriptionAnnotation.parse("${TextDoc::class.java.name}#") }
                    .message shouldContain "no attribute name after '#'"
            }
        }

        given("of: 같은 어노테이션의 같은 속성") {
            then("hashCode 가 같아 집합에서 하나로 합쳐진다") {
                val first = DescriptionAnnotation.of(TextDoc::class, "text")
                val second = DescriptionAnnotation.parse("${TextDoc::class.java.name}#text")
                val third = DescriptionAnnotation.of(LabelDoc::class)

                first.hashCode() shouldBe second.hashCode()
                listOf(first, second, third).toSet() shouldBe setOf(first, third)
                DescriptionAnnotation.of(LabelDoc::class, "description") shouldNotBe third
            }
        }

        given("from: 함수로 읽기") {
            then("자기 자신과만 같다") {
                val read: (TextDoc) -> String? = { it.text }
                val first = DescriptionAnnotation.from(read)

                first shouldBe first
                first shouldNotBe DescriptionAnnotation.from(read)
                first.attribute shouldBe null
                first.annotationClass shouldBe TextDoc::class
            }

            then("KClass 를 받는 버전도 같은 어노테이션을 가리킨다") {
                val byClass = DescriptionAnnotation.from(TextDoc::class) { it.text }

                byClass.annotationClass shouldBe TextDoc::class
                byClass.attribute shouldBe null
                setOf(byClass, DescriptionAnnotation.from(TextDoc::class) { it.text }).size shouldBe 2
            }
        }
    })
