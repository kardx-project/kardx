package io.github.kardx.internal.output

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SnippetPathsTest :
    BehaviorSpec({
        given("camelCase 문자열이 주어졌을 때") {
            val cases = mapOf(
                "getUserInfo" to "get-user-info",
                "UserControllerTest" to "user-controller-test",
                "UserControllerV2Test" to "user-controller-v2-test",
                "getUser2Info" to "get-user2-info",
                "HTMLParser" to "html-parser",
                "" to "",
            )

            cases.forEach { (input, expected) ->
                `when`("\"$input\" 을 변환하면") {
                    then("\"$expected\" 가 된다") {
                        SnippetPaths.camelCaseToDash(input) shouldBe expected
                    }
                }
            }

            `when`("빈 문자열을 변환하면") {
                then("빈 문자열이 된다") {
                    SnippetPaths.camelCaseToDash("") shouldBe ""
                }
            }
        }
    })
