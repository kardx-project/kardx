package io.github.kardx.dsl

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/** 수식어 의미: `optional()`·`ignored()` 는 켜고, `isOptional false`·`isIgnored false` 는 아무것도 바꾸지 않는다. */
class DslModifierTest :
    BehaviorSpec({
        /** 타입마다 (새로 만들기, optional 여부) 를 같은 모양으로 맞춘다. */
        data class Kind<T>(
            val name: String,
            val create: () -> T,
            val isOptional: (T) -> Boolean,
            val optional: (T) -> T,
            val isOptionalInfix: (T, Boolean) -> T,
        )

        val kinds = listOf(
            Kind(
                "RestDocsField",
                { "a" type STRING means "A" },
                ::fieldOptional,
                RestDocsField::optional,
                RestDocsField::isOptional,
            ),
            Kind(
                "RestDocsParam",
                { "a" queryMeans "A" },
                ::paramOptional,
                RestDocsParam::optional,
                RestDocsParam::isOptional,
            ),
            Kind(
                "RestDocsHeader",
                { "a" headerMeans "A" },
                ::headerOptional,
                RestDocsHeader::optional,
                RestDocsHeader::isOptional,
            ),
            Kind(
                "RestDocsCookie",
                { "a" cookieMeans "A" },
                ::cookieOptional,
                RestDocsCookie::optional,
                RestDocsCookie::isOptional,
            ),
            Kind(
                "RestDocsLink",
                { "a" linkMeans "A" },
                ::linkOptional,
                RestDocsLink::optional,
                RestDocsLink::isOptional,
            ),
            Kind(
                "RestDocsPart",
                { "a" partMeans "A" },
                ::partOptional,
                RestDocsPart::optional,
                RestDocsPart::isOptional,
            ),
            Kind(
                "RestDocsEvent",
                { "a" eventMeans "A" },
                ::eventOptional,
                { it.optional() },
                RestDocsEvent::isOptional,
            ),
        )

        kinds.forEach { kind ->
            @Suppress("UNCHECKED_CAST")
            val k = kind as Kind<Any>

            given("${k.name} 의 optional 수식어") {
                `when`("optional() 을 부르면") {
                    then("optional 이 된다") {
                        k.isOptional(k.optional(k.create())) shouldBe true
                    }
                }

                `when`("isOptional true 이면") {
                    then("optional 이 된다") {
                        k.isOptional(k.isOptionalInfix(k.create(), true)) shouldBe true
                    }
                }

                `when`("새 항목에 isOptional false 이면") {
                    then("optional 을 켜지 않는다") {
                        k.isOptional(k.isOptionalInfix(k.create(), false)) shouldBe false
                    }
                }

                `when`("optional 인 항목에 isOptional false 이면") {
                    then("optional 을 끄지 않는다") {
                        k.isOptional(k.isOptionalInfix(k.optional(k.create()), false)) shouldBe true
                    }
                }
            }
        }

        given("RestDocsField 의 ignored 수식어") {
            `when`("ignored() 를 부르면") {
                then("ignored 가 된다") {
                    ("a" type STRING means "A").ignored().descriptor.isIgnored shouldBe true
                }
            }

            `when`("isIgnored true 이면") {
                then("ignored 가 된다") {
                    ("a" type STRING means "A" isIgnored true).descriptor.isIgnored shouldBe true
                }
            }

            `when`("isIgnored false 이면") {
                then("켜지도 끄지도 않는다") {
                    ("a" type STRING means "A" isIgnored false).descriptor.isIgnored shouldBe false
                    ("a" type STRING means "A").ignored().isIgnored(false).descriptor.isIgnored shouldBe true
                }
            }
        }
    })

private fun fieldOptional(item: RestDocsField) = item.descriptor.isOptional

private fun paramOptional(item: RestDocsParam) = item.descriptor.isOptional

private fun headerOptional(item: RestDocsHeader) = item.descriptor.isOptional

private fun cookieOptional(item: RestDocsCookie) = item.descriptor.isOptional

private fun linkOptional(item: RestDocsLink) = item.descriptor.isOptional

private fun partOptional(item: RestDocsPart) = item.descriptor.isOptional

private fun eventOptional(item: RestDocsEvent) = item.optional
