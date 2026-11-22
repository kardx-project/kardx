package io.github.kardx.internal.sse

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly

class SseParserTest :
    BehaviorSpec({
        given("text/event-stream 본문") {
            `when`("Spring SseEmitter 형식(콜론 뒤 공백 없음)이면") {
                then("이벤트 이름, data, id 를 읽는다") {
                    SseParser.parse("event:a\ndata:{\"x\":1}\n\nid:7\nevent:b\ndata:{}\n\n") shouldContainExactly
                        listOf(
                            SseEvent("a", """{"x":1}""", null),
                            SseEvent("b", "{}", "7"),
                        )
                }
            }

            `when`("주석, 여러 줄 data, 이름 없는 이벤트, CRLF 가 섞여 있으면") {
                then("주석은 무시하고 data 는 개행으로 잇고 기본 이름은 message 다") {
                    SseParser.parse(
                        ": keep-alive\r\ndata: line1\r\ndata: line2\r\n\r\nevent: ping\r\n\r\n",
                    ) shouldContainExactly
                        listOf(
                            SseEvent("message", "line1\nline2", null),
                        )
                }
            }

            `when`("마지막 이벤트 뒤에 빈 줄이 없으면") {
                then("그 이벤트도 포함한다") {
                    SseParser.parse("event:last\ndata:1") shouldContainExactly listOf(SseEvent("last", "1", null))
                }
            }
        }
    })
