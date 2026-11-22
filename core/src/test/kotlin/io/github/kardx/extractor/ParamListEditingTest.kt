package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.dsl.queryMeans
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private class SearchCondition(
    @Description("검색어") val keyword: String,
    @Description("페이지") val page: Int,
    @Description("디버그 출력") val debug: Boolean,
)

private fun List<RestDocsParam>.names() = map { it.name }

private fun List<RestDocsParam>.optionalNames() = filter { it.descriptor.isOptional }
    .map { it.name }

/** 파라미터 목록의 `except`·`optionalAt`. */
class ParamListEditingTest :
    BehaviorSpec({
        given("RestDocsParam.name") {
            then("디스크립터의 이름이다") {
                ("page" queryMeans "페이지").name shouldBe "page"
            }
        }

        given("except") {
            then("그 이름의 파라미터를 뺀다") {
                parametersOf<SearchCondition>().except("debug")
                    .names() shouldContainExactly listOf("keyword", "page")
            }

            then("목록에 없는 이름이면 오타로 남지 않도록 실패한다") {
                val message = shouldThrow<IllegalArgumentException> {
                    parametersOf<SearchCondition>().except("debugg")
                }.message

                message shouldContain "kardx[KDX-108]: "
                message shouldContain "except: no parameter named debugg. Parameters: keyword, page, debug"
            }
        }

        given("optionalAt") {
            val original = parametersOf<SearchCondition>()
            val params = original.optionalAt("keyword", "page")

            then("그 파라미터만 optional 이다") {
                params.optionalNames() shouldContainExactly listOf("keyword", "page")
            }

            then("원래 목록의 파라미터는 바꾸지 않고 설명·속성은 복사한다") {
                original.optionalNames() shouldContainExactly emptyList()
                params.first().descriptor.description shouldBe original.first().descriptor.description
                params.first().descriptor.attributes shouldBe original.first().descriptor.attributes
            }

            then("목록에 없는 이름이면 실패한다") {
                shouldThrow<IllegalArgumentException> { original.optionalAt("size") }
                    .message shouldContain "optionalAt: no parameter named size"
            }
        }
    })
