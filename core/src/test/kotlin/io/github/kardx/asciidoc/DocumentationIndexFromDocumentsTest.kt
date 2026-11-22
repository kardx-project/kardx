package io.github.kardx.asciidoc

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.output.DocumentRecord
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Order
import java.io.File
import java.nio.file.Files

@Order(1)
@DisplayName("주문 API")
class FirstOrderedDocs

@DisplayName("가 API")
class UnorderedDocs

/** 문서 디렉터리에 클래스의 문서(`.adoc`)와 index 항목을 남긴다. 테스트가 문서를 쓸 때와 같다. */
private fun File.writeDocument(documentClass: Class<*>, section: String) {
    val entry = DocumentRecord.of(documentClass)

    File(this, "${entry.documentName}.adoc").writeText("= ${entry.title}\n\n[[$section]]\n== $section\n")
    entry.write(this)
}

class DocumentationIndexFromDocumentsTest :
    BehaviorSpec({
        given("테스트가 문서와 함께 남긴 index 항목") {
            val documents = Files.createTempDirectory("index-documents").toFile()

            documents.writeDocument(UnorderedDocs::class.java, "list")
            documents.writeDocument(FirstOrderedDocs::class.java, "create")
            // 문서(.adoc)가 지워진 항목은 링크가 깨지므로 넣지 않는다
            DocumentRecord.of(MemberDocs::class.java)
                .write(documents)

            `when`("템플릿 없이 index 를 만들면") {
                val output = File(documents.parentFile, "index-${documents.name}/index.adoc")

                DocumentationIndex.writeFromDocuments(null, "주문 서비스", output, documents)

                val index = output.readText()

                then("내장 템플릿의 제목과 생성 문서와 같은 헤더 속성을 쓴다") {
                    index shouldStartWith "= 주문 서비스\n:doctype: book\n:icons: font\n"
                    index shouldContain ":toc: left\n"
                    index shouldContain "== API list\n\n[[first-ordered-docs]]"
                    index shouldContain "link:./first-ordered-docs.html[Open document]"
                }

                then("@Order 순서, 같으면 제목 순서로 넣고 문서가 없는 항목은 뺀다") {
                    (index.indexOf("[[first-ordered-docs]]") < index.indexOf("[[unordered-docs]]")) shouldBe true
                    index shouldContain "* link:./unordered-docs.html#list[list]"
                    index shouldNotContain "member-docs"
                }
            }

            `when`("language 가 ko 면") {
                val output = File(documents.parentFile, "index-ko-${documents.name}/index.adoc")

                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    DocumentationIndex.writeFromDocuments(null, "주문 서비스", output, documents)
                }

                val index = output.readText()

                then("목록 제목과 링크 글자가 한국어다") {
                    index shouldContain "== API 목록\n\n[[first-ordered-docs]]"
                    index shouldContain "link:./first-ordered-docs.html[문서 열기]"
                }
            }

            `when`("같은 클래스로 패키지를 훑어 만든 index 와 비교하면") {
                val template = File(documents.parentFile, "template-${documents.name}.adoc").apply {
                    writeText("= API\n\n${DocumentationIndex.MARKER}\n")
                }
                val scanned = File(documents.parentFile, "scanned-${documents.name}.adoc")
                val fromDocuments = File(documents.parentFile, "from-${documents.name}.adoc")

                DocumentationIndex.write(
                    template,
                    scanned,
                    listOf(UnorderedDocs::class.java, FirstOrderedDocs::class.java),
                    documents,
                )
                DocumentationIndex.writeFromDocuments(template, "무시", fromDocuments, documents)

                then("내용이 같다") {
                    fromDocuments.readText() shouldBe scanned.readText()
                }
            }
        }

        given("index 항목 파일") {
            val documents = Files.createTempDirectory("index-entry").toFile()

            DocumentRecord.of(FirstOrderedDocs::class.java)
                .write(documents)

            val file = File(documents, "${DocumentRecord.DIRECTORY}/first-ordered-docs.properties")
            val first = file.readText()

            DocumentRecord.of(FirstOrderedDocs::class.java)
                .write(documents)

            then("내용이 같으면 파일도 같다 (날짜 주석이 없다)") {
                file.readText() shouldBe first
                first shouldNotContain "#"
            }

            then("다시 읽으면 같은 항목이다") {
                val entry = DocumentRecord.readAll(documents)
                    .single()

                entry.documentName shouldBe "first-ordered-docs"
                entry.title shouldBe "주문 API"
                entry.order shouldBe 1
                entry.className shouldBe FirstOrderedDocs::class.java.name
            }
        }

        given("CLI 옵션") {
            then("--package 없이 주면 훑지 않고 템플릿·제목은 선택이다") {
                val options = DocumentationIndexCliOptions.parse(arrayOf("--output", "out/index.adoc"))

                options.scan.shouldBeNull()
                options.template.shouldBeNull()
                options.title shouldBe "API Documentation"
            }

            then("--title 이 없으면 제목은 language 를 따른다") {
                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    DocumentationIndexCliOptions.parse(arrayOf("--output", "out/index.adoc")).title shouldBe "API 문서"
                }
            }

            then("--title 을 받는다") {
                DocumentationIndexCliOptions.parse(arrayOf("--output", "o.adoc", "--title", "주문")).title shouldBe "주문"
            }

            then("--package 와 --base-class 는 함께 준다") {
                shouldThrow<IllegalArgumentException> {
                    DocumentationIndexCliOptions.parse(arrayOf("--output", "o.adoc", "--package", "p"))
                }.message shouldBe "--package and --base-class go together"
                DocumentationIndexCliOptions.parse(arrayOf("--output", "o.adoc", "--package", "p", "--base-class", "B"))
                    .scan
                    .shouldNotBeNull()
            }
        }
    })
