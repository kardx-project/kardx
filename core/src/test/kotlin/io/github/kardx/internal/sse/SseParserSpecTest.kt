package io.github.kardx.internal.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SseParserSpecTest :
    BehaviorSpec({
        given("BOM 으로 시작하는 스트림과 빈 event 줄") {
            then("BOM 은 무시하고 빈 이벤트 이름은 message 로 본다") {
                val events = SseParser.parse("\uFEFFevent:a\ndata:1\n\nevent:\ndata:2\n\n")

                events.map { it.name } shouldBe listOf("a", "message")
                events.map { it.data } shouldBe listOf("1", "2")
            }
        }
    })
