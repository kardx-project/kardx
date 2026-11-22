package io.github.kardx.kotest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldMatch
import java.text.Normalizer

class TestNamesTest :
    FunSpec({
        test("한글·공백·괄호는 한글을 남기고 공백·기호를 '-' 로 바꾼다") {
            TestNames.snippetName(listOf("사용자가 있을 때", "단건 조회하면 (성공)")) shouldBe "사용자가-있을-때-단건-조회하면-성공"
        }

        test("경로·URL 에 문제 되는 글자(/ . : ? # 공백)를 남기지 않는다") {
            TestNames.snippetName(listOf("GET /api/v1.2/users/{id}?x=1#top", "a:b")) shouldBe
                "get-api-v1-2-users-id-x-1-top-a-b"
        }

        test("camelCase 는 REST Docs 처럼 낱말을 나누고 모두 소문자로 바꾼다") {
            TestNames.slug("getUserInfo") shouldBe "get-user-info"
            TestNames.slug("API 조회") shouldBe "api-조회"
        }

        test("자모가 풀린(NFD) 한글도 합쳐서 같은 이름이 된다") {
            TestNames.slug(Normalizer.normalize("조회", Normalizer.Form.NFD)) shouldBe "조회"
        }

        test("섹션 제목은 이름을 ' - ' 로 잇는다") {
            TestNames.sectionTitle(listOf("목록", "첫 페이지")) shouldBe "목록 - 첫 페이지"
        }

        test("기호뿐인 이름은 빈 조각을 건너뛰고, 모두 비면 test") {
            TestNames.snippetName(listOf("!!!", "조회")) shouldBe "조회"
            TestNames.safe("???") shouldBe "test"
        }

        test("너무 긴 이름은 잘라 내고 전체 이름의 해시를 붙인다 (늘 같은 값)") {
            val names = listOf("아주 긴 테스트 이름".repeat(20))
            val name = TestNames.snippetName(names)

            name.toByteArray().size shouldBeLessThanOrEqual TestNames.MAX_SNIPPET_NAME_BYTES
            name shouldMatch Regex("""[\p{L}\-]+-[0-9a-f]{8}""")
            TestNames.snippetName(names) shouldBe name
            name shouldEndWith name.takeLast(8)
        }

        test("사용자 규칙이 돌려준 이름도 안전하게 다듬는다 (이미 안전하면 그대로)") {
            TestNames.safe("Custom Name/With.Dots") shouldBe "custom-name-with-dots"
            TestNames.safe("사용자-조회") shouldBe "사용자-조회"
        }
    })
