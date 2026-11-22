package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.github.kardx.dsl.eventMeans
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

private class SoldOut(@Description("상품 ID") val productId: Long, @Description("메모") val note: String?)

/** SSE 이벤트의 `withFields Event::class`. */
class EventFieldTypesTest :
    BehaviorSpec({
        given("이벤트에 클래스를 주면") {
            val event = "sold-out" eventMeans "품절" withFields SoldOut::class

            then("응답 규칙으로 추출한 목록과 같은 필드를 쓴다") {
                event.fields.map { it.descriptor.path } shouldContainExactly
                    responseFieldsOf<SoldOut>().map { it.descriptor.path }
                event.fields.filter { it.descriptor.isOptional }
                    .map { it.descriptor.path } shouldContainExactly listOf("note")
            }

            then("다른 수식어와 이어 쓸 수 있다") {
                ("restocked" eventMeans "재입고" isOptional true withFields SoldOut::class).optional shouldBe true
            }
        }
    })
