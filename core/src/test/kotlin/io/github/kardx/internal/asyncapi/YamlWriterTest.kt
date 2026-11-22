package io.github.kardx.internal.asyncapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class YamlWriterTest :
    BehaviorSpec({
        given("YAML 1.1 예약어처럼 보이는 키") {
            then("따옴표로 감싸서 불리언·null 키로 읽히지 않게 한다") {
                val yaml = YamlWriter.write(linkedMapOf("on" to 1, "y" to 2, "null" to 3, "name" to 4))

                yaml shouldBe "\"on\": 1\n\"y\": 2\n\"null\": 3\nname: 4\n"
            }
        }

        given("유니코드 줄바꿈 문자가 든 문자열") {
            then("이스케이프해서 한 줄로 남긴다") {
                YamlWriter.write(mapOf("text" to "a\u0085b\u2028c")) shouldContain "\"a\\u0085b\\u2028c\""
            }
        }
    })
