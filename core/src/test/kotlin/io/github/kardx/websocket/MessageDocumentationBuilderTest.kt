package io.github.kardx.websocket

import io.github.kardx.dsl.MessageDirection
import io.github.kardx.dsl.MessageDocumentationBuilder
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.headerMeans
import io.github.kardx.dsl.type
import io.github.kardx.internal.websocket.MessageSpec
import io.github.kardx.internal.websocket.toSpec
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class MessageDocumentationBuilderTest :
    BehaviorSpec({
        given("빈 블록") {
            `when`("만들면") {
                val spec = MessageDocumentationBuilder(MessageDirection.SEND).toSpec()

                then("기본값을 쓴다") {
                    spec shouldBe MessageSpec()
                }
            }
        }

        given("목록과 가변 인자를 섞어 여러 번 부를 때") {
            `when`("만들면") {
                val spec =
                    MessageDocumentationBuilder(MessageDirection.SEND).apply {
                        description = "설명"
                        name = "chatSend"
                        relaxed = true
                        headerValues("a" to "1")
                        headerValues(mapOf("b" to "2"))
                        headers("a" headerMeans "A")
                        headers(listOf("b" headerMeans "B"))
                        fields("x" type STRING means "X")
                        fields(listOf("y" type STRING means "Y"))
                    }.toSpec()

                then("항목이 순서대로 이어 붙는다") {
                    spec.description shouldBe "설명"
                    spec.name shouldBe "chatSend"
                    spec.relaxed shouldBe true
                    spec.headerValues shouldContainExactly mapOf("a" to "1", "b" to "2")
                    spec.headers.map { it.descriptor.name } shouldContainExactly listOf("a", "b")
                    spec.fields.map { it.descriptor.path } shouldContainExactly listOf("x", "y")
                }
            }
        }

        given("같은 헤더 이름을 다시 줄 때") {
            `when`("만들면") {
                val spec =
                    MessageDocumentationBuilder(MessageDirection.SEND).apply {
                        headerValues("a" to "1")
                        headerValues("a" to "2")
                    }.toSpec()

                then("나중 값이 이긴다") {
                    spec.headerValues shouldContainExactly mapOf("a" to "2")
                }
            }
        }

        given("header(이름, 값, 설명)") {
            `when`("만들면") {
                val spec =
                    MessageDocumentationBuilder(MessageDirection.SEND).apply {
                        header("Authorization", "Bearer t", "토큰")
                    }.toSpec()

                then("헤더 값과 디스크립터를 함께 더한다") {
                    spec.headerValues shouldContainExactly mapOf("Authorization" to "Bearer t")
                    spec.headers.single().descriptor.name shouldBe "Authorization"
                    spec.headers.single().descriptor.description shouldBe "토큰"
                }
            }
        }
    })
