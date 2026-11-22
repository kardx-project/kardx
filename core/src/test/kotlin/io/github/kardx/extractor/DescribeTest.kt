package io.github.kardx.extractor

import io.github.kardx.annotation.Description
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import io.github.kardx.dsl.subsection
import io.github.kardx.dsl.type
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.validation.constraints.Size
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class PreviewUser(
    @Description("사용자 ID")
    val id: Long,
    @Description("닉네임")
    @field:Size(min = 2, max = 10)
    val nickname: String?,
    val tags: List<String>,
)

class PreviewCondition(
    @Description("검색어")
    val keyword: String?,
    val page: Int = 0,
)

class DescribeTest :
    BehaviorSpec({
        given("추출한 응답 필드") {
            val table = responseFieldsOf<PreviewUser>().describe()
            val rows = table.lines()
                .map { line ->
                    line.split("|")
                        .map(String::trim)
                        .drop(1)
                        .dropLast(1)
                }

            then("AsciiDoc 표와 같은 열의 글자 표를 만든다") {
                rows.first() shouldBe listOf("Path", "Type", "Required", "Description", "Constraints")
                rows.single { it.first() == "id" } shouldBe listOf("id", "Number", "true", "사용자 ID", "")
                rows.single { it.first() == "tags" } shouldBe listOf("tags", "Array", "true", "No description", "")
            }

            then("제약은 Constraints 열에 나온다") {
                rows.single { it.first() == "nickname" }[4] shouldContain "Size must be between 2 and 10"
            }

            then("한글은 두 칸으로 세어 열을 맞춘다") {
                val idLine = table.lines()
                    .single { it.startsWith("| id ") }
                val tagsLine = table.lines()
                    .single { it.startsWith("| tags ") }

                // "사용자 ID" 는 9칸, "No description" 은 14칸이라 사용자 ID 뒤에 공백이 5개 더 붙는다
                idLine shouldContain "| 사용자 ID      |"
                tagsLine shouldContain "| No description |"
            }
        }

        given("직접 기술한 필드") {
            then("기본값·예시·Deprecated 표시를 설명 칸에 붙이고 ignored 는 뺀다") {
                val table = listOf(
                    "size" type NUMBER means "크기" withDefaultValue "20" example "10",
                    "old" type STRING means "옛 이름" deprecated "name 을 쓴다",
                    "inner" type STRING means "내부" isIgnored true,
                    "meta" subsection OBJECT means "자유 형식",
                ).describe()

                table shouldContain "크기 (Default: 20) (Example: 10)"
                table shouldContain "[Deprecated: name 을 쓴다] 옛 이름"
                table shouldContain "| meta | Object (subsection) |"
                table shouldNotContain "inner"
            }
        }

        given("추출한 파라미터") {
            then("Parameter 열의 표를 만들고(생성자 기본값 포함) printDescription 은 목록을 그대로 돌려준다") {
                val parameters = parametersOf<PreviewCondition>()
                val out = ByteArrayOutputStream()
                val original = System.out

                System.setOut(PrintStream(out, true, Charsets.UTF_8))

                val returned = try {
                    parameters.printDescription()
                } finally {
                    System.setOut(original)
                }

                returned shouldBe parameters
                val printed = out.toString(Charsets.UTF_8)

                printed shouldContain "| Parameter | Required | Description                 | Constraints |"
                printed shouldContain "| keyword   | false    | 검색어                      |"
                printed shouldContain "| page      | false    | No description (Default: 0) |"
            }
        }
    })
