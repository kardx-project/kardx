package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass

@Suppress("unused")
class DescribedKotlin(
    @Description("기본 위치") val plain: String,
    @property:Description("프로퍼티") val onProperty: String,
    @field:Description("필드") val onField: String,
    @get:Description("getter") val onGetter: String,
    @set:Description("setter") var onSetter: String,
) {
    @Description("본문 프로퍼티")
    val inBody: String = "b"

    @set:Description("본문 setter")
    var inBodySetter: String = "s"
}

private fun descriptions(type: KClass<*>): Map<String, Any?> = responseFieldsOf(type)
    .associate { it.descriptor.path to it.descriptor.description }

class DescriptionTargetTest :
    BehaviorSpec({
        given("Kotlin 클래스") {
            then("@Description 을 어느 위치에 붙여도 읽는다") {
                descriptions(DescribedKotlin::class) shouldBe mapOf(
                    "plain" to "기본 위치",
                    "onProperty" to "프로퍼티",
                    "onField" to "필드",
                    "onGetter" to "getter",
                    "onSetter" to "setter",
                    "inBody" to "본문 프로퍼티",
                    "inBodySetter" to "본문 setter",
                )
            }

            then("요청 본문과 파라미터에서도 같다") {
                requestFieldsOf(DescribedKotlin::class)
                    .first()
                    .descriptor.description shouldBe "기본 위치"
                parametersOf(DescribedKotlin::class)
                    .first { it.descriptor.name == "onSetter" }
                    .descriptor.description shouldBe "setter"
            }
        }

        given("Java bean") {
            then("필드·getter·setter 에 붙인 설명을 읽는다") {
                descriptions(DescribedJavaBean::class) shouldBe mapOf(
                    "onField" to "필드에 붙인 설명",
                    "onGetter" to "getter 에 붙인 설명",
                    "onSetter" to "setter 에 붙인 설명",
                )
            }
        }

        given("Java record") {
            then("record component 에 붙인 설명을 읽는다") {
                descriptions(DescribedJavaRecord::class) shouldBe mapOf("code" to "record 설명")
            }
        }
    })
