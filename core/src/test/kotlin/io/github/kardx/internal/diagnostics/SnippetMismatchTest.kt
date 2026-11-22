package io.github.kardx.internal.diagnostics

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.Snippet
import org.springframework.restdocs.snippet.SnippetException
import java.lang.reflect.Proxy

/** 스니펫이 들여다보지 않는 자리에 넘기는 빈 Operation. */
private val NO_OPERATION = Proxy.newProxyInstance(Operation::class.java.classLoader, arrayOf(Operation::class.java)) {
        _,
        _,
        _,
    ->
    null
} as Operation

class SnippetMismatchTest :
    BehaviorSpec({
        given("REST Docs 필드 검증 실패 메시지") {
            val message = listOf(
                "The following parts of the payload were not documented:",
                """{ "data" : { "memo" : "m", "tags" : [ "a" ], "items" : [ { "price" : 1 } ] }, "ok" : true }""",
                "Fields with the following paths were not found in the payload: [data.name, data.items[].count]",
            ).joinToString("\n")

            `when`("읽으면") {
                val mismatch = SnippetMismatch.parse(message).shouldNotBeNull()

                then("문서화하지 않은 값을 경로와 JSON 타입으로 펼친다") {
                    mismatch.isFields shouldBe true
                    mismatch.undocumented.map { "${it.path}:${it.type}" } shouldContainExactly listOf(
                        "data.memo:String",
                        "data.tags:Array",
                        "data.items[].price:Number",
                        "ok:Boolean",
                    )
                }

                then("없는 경로를 목록으로 읽는다") {
                    mismatch.missing shouldContainExactly listOf("data.name", "data.items[].count")
                }
            }
        }

        given("이름 목록 메시지 (파라미터·헤더·이벤트)") {
            then("주어와 문서화하지 않은·없는 이름을 읽는다") {
                val mismatch = SnippetMismatch.parse(
                    "Query parameters with the following names were not documented: [page, size]. " +
                        "Query parameters with the following names were not found in the request: [sort]",
                ).shouldNotBeNull()

                mismatch.subject shouldBe "Query parameters"
                mismatch.undocumented.map { it.path } shouldContainExactly listOf("page", "size")
                mismatch.missing shouldContainExactly listOf("sort")
            }

            then("검증이 아닌 실패는 읽지 않는다") {
                SnippetMismatch.parse("Cannot document response fields as the response body is empty") shouldBe null
            }
        }

        given("JSON 이 아닌 본문") {
            then("원문을 그대로 남긴다") {
                val mismatch = SnippetMismatch.parse(
                    "The following parts of the payload were not documented:\n<user><memo>m</memo></user>",
                ).shouldNotBeNull()

                mismatch.undocumentedText shouldBe "<user><memo>m</memo></user>"
                mismatch.undocumented shouldBe emptyList()
            }
        }

        given("감싼 스니펫") {
            then("알아볼 수 없는 실패는 원래 예외를 그대로 던진다") {
                val original = SnippetException("Something else went wrong")
                val snippet = Snippet { throw original }

                val thrown = shouldThrow<SnippetException> {
                    snippet.diagnosed("response fields", "response")
                        .document(NO_OPERATION)
                }

                thrown shouldBeSameInstanceAs original
            }
        }
    })
