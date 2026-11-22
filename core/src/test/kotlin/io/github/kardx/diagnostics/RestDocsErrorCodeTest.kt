package io.github.kardx.diagnostics

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

class RestDocsErrorCodeTest :
    BehaviorSpec({
        given("오류 코드 목록") {
            val codes = RestDocsErrorCode.entries

            then("코드는 KDX- 와 세 자리 숫자이고 서로 다르다") {
                codes.forEach { it.code shouldMatch Regex("KDX-\\d{3}") }
                codes.groupBy { it.code }
                    .filterValues { it.size > 1 }
                    .keys
                    .shouldBeEmpty()
            }

            then("코드 순서대로 적혀 있다 (문제 해결 문서와 같은 순서)") {
                codes.map { it.code } shouldBe codes.map { it.code }.sorted()
            }

            then("요약은 비어 있지 않은 한 줄이다") {
                codes.filter { it.summary.isBlank() || '\n' in it.summary }.shouldBeEmpty()
            }

            then("코드로 찾는다 (대소문자 무시)") {
                RestDocsErrorCode.of("kdx-011") shouldBe RestDocsErrorCode.FOREIGN_DOCUMENTATION_CONTEXT
                RestDocsErrorCode.of("KDX-999") shouldBe null
            }
        }

        given("메시지 형식") {
            val code = RestDocsErrorCode.INVALID_SCOPE

            then("한 줄 메시지는 코드로 시작하고 문서 주소로 끝난다") {
                code.message("invalid scope.") shouldBe
                    "kardx[KDX-203]: invalid scope. See ${RestDocsErrorCode.TROUBLESHOOTING_URL}#kdx-203"
            }

            then("마침표가 없으면 붙인다") {
                code.message("invalid scope") shouldBe
                    "kardx[KDX-203]: invalid scope. See ${RestDocsErrorCode.TROUBLESHOOTING_URL}#kdx-203"
            }

            then("여러 줄 메시지는 빈 줄 뒤에 주소를 둔다") {
                code.message(listOf("first", "second").joinToString("\n")) shouldBe listOf(
                    "kardx[KDX-203]: first",
                    "second",
                    "",
                    "See ${RestDocsErrorCode.TROUBLESHOOTING_URL}#kdx-203",
                ).joinToString("\n")
            }
        }
    })
