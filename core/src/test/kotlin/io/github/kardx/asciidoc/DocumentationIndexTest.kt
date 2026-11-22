package io.github.kardx.asciidoc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.DisplayName
import java.io.File
import java.nio.file.Files

abstract class IndexBase

@DisplayName("회원 API")
class MemberDocs : IndexBase()

class OrderDocs : IndexBase()

/** 패키지 스캔에서 빠져야 하는 추상 클래스. */
abstract class AbstractDocs : IndexBase()

/** 패키지 스캔에서 빠져야 하는, 기본 클래스를 상속하지 않은 클래스. */
class IndexTestFixture

class DocumentationIndexTest :
    BehaviorSpec({
        given("템플릿과 생성된 문서") {
            val dir = Files.createTempDirectory("index").toFile()
            val template = File(dir, "index-template.adoc").apply {
                writeText("= API\n\n== 목록\n\n${DocumentationIndex.MARKER}\n\n== 부록\n")
            }

            File(dir, "member-docs.adoc").writeText(
                "= 회원 API\n\n[[get-member]]\n== icon:link[] 회원 조회\n\n[[list]]\n== icon:link[] 목록 [배열]\n",
            )

            val output = File(dir, "index.adoc")

            DocumentationIndex.write(template, output, listOf(OrderDocs::class.java, MemberDocs::class.java))

            val index = output.readText()

            then("마커 자리에 항목과 섹션 링크를 넣고, 문서(.adoc)가 없는 클래스는 넣지 않는다") {
                index shouldContain
                    "== 목록\n\n[[member-docs]]\n=== 회원 API\n\nlink:./member-docs.html[Open document]\n\n" +
                    "* link:./member-docs.html#get-member[회원 조회]\n"
                index shouldNotContain "order-docs"
                index shouldNotContain DocumentationIndex.MARKER
                index shouldEndWith "== 부록\n"
            }

            then("링크 글자의 ] 는 이스케이프한다") {
                index shouldContain "* link:./member-docs.html#list[목록 [배열\\]]"
            }

            then("마커가 없으면 끝에 붙인다") {
                template.writeText("= API\n")

                DocumentationIndex.write(template, output, listOf(MemberDocs::class.java))

                output.readText() shouldContain "= API\n\n[[member-docs]]"
            }

            then("출력이 템플릿과 같으면 거부한다") {
                shouldThrow<IllegalArgumentException> { DocumentationIndex.write(template, template, emptyList()) }
            }
        }

        given("테스트 문서와 다른 디렉터리에 만드는 index") {
            val dir = Files.createTempDirectory("index").toFile()
            val documents = Files.createDirectories(dir.toPath().resolve("generated-asciidoc")).toFile()
            val template = File(dir, "index-template.adoc")
            val output = File(dir, "index/index.adoc")

            File(documents, "member-docs.adoc").writeText("= 회원 API\n\n[[get-member]]\n== 회원 조회\n")
            template.writeText("= API\n\n${DocumentationIndex.MARKER}\n")

            `when`("문서 디렉터리를 주면") {
                DocumentationIndex.write(template, output, listOf(MemberDocs::class.java), documents)

                then("그 디렉터리의 문서로 항목을 만들고, 출력 디렉터리가 없으면 만든다") {
                    output.readText() shouldContain "* link:./member-docs.html#get-member[회원 조회]"
                }
            }

            `when`("문서 디렉터리를 주지 않으면") {
                DocumentationIndex.write(template, output, listOf(MemberDocs::class.java))

                then("출력 디렉터리에서 찾으므로 문서가 없어 항목을 넣지 않는다") {
                    output.readText() shouldNotContain "member-docs"
                }
            }
        }

        given("패키지 스캔") {
            then("기본 클래스를 상속한 구체 클래스만 찾는다") {
                DocumentationIndex.findTestClasses("io.github.kardx.asciidoc", IndexBase::class)
                    .map { it.simpleName } shouldContainExactlyInAnyOrder listOf("MemberDocs", "OrderDocs")
            }
        }

        given("CLI 옵션") {
            then("필수 옵션이 없거나 모르는 옵션이면 실패한다") {
                shouldThrow<IllegalArgumentException> { DocumentationIndexCliOptions.parse(arrayOf("--template", "a")) }
                    .message shouldBe "--output is required"
                shouldThrow<IllegalArgumentException> { DocumentationIndexCliOptions.parse(arrayOf("--nope", "x")) }
            }

            val required = arrayOf(
                "--template",
                "t.adoc",
                "--output",
                "out/index.adoc",
                "--package",
                "p",
                "--base-class",
                "B",
            )

            then("--documents 가 없으면 출력 파일의 디렉터리를 쓴다") {
                DocumentationIndexCliOptions.parse(required).documents shouldBe
                    File("out/index.adoc").absoluteFile.parentFile
            }

            then("--documents 를 주면 그 디렉터리를 쓴다") {
                DocumentationIndexCliOptions.parse(required + arrayOf("--documents", "docs")).documents shouldBe
                    File("docs")
            }
        }
    })
