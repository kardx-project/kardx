@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.internal.asciidoc

import io.github.kardx.annotation.AsciiDescription
import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.junit.JUnitDocumentOutline
import io.github.kardx.lifecycle.AsciiDocumentWriter
import io.github.kardx.lifecycle.RestDocsExtensionApi
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

@Disabled("문서 생성 대상 픽스처")
abstract class BaseDocTest {
    @Test
    @DisplayName("상속된 조회")
    fun inheritedRead() = Unit
}

@Disabled("문서 생성 대상 픽스처")
@DisplayName("샘플 API")
@AsciiDescription("설명", bracket = AsciiDescription.Bracket.SQUARE)
class SampleDocTest : BaseDocTest() {
    @Test
    @DisplayName("업로드")
    fun upload() = Unit

    @Test
    fun undocumented() = Unit

    @ParameterizedTest
    @ValueSource(strings = ["a"])
    fun byParameter(value: String) = check(value.isNotEmpty())

    @RepeatedTest(2)
    fun repeated() = Unit

    @Test
    @DisplayName("공백 메서드")
    fun `공백 있는 메서드 (백틱)`() = Unit
}

@Disabled("문서 생성 대상 픽스처")
@DisplayName("빈 API")
class EmptyDocTest {
    @Test
    fun nothing() = Unit
}

@Disabled("문서 생성 대상 픽스처")
@DisplayName("빈 설명 API")
@AsciiDescription("")
class BlankDescriptionDocTest {
    @Test
    @DisplayName("공백 설명")
    @AsciiDescription("   ")
    fun blank() = Unit
}

private fun Path.snippet(method: String, vararg files: String) {
    val dir = resolve("sample-doc-test").resolve(method)

    Files.createDirectories(dir)
    files.forEach { Files.writeString(dir.resolve(it), "") }
}

/** 모든 문서 머리에 include 할 파일을 정한 설정. */
private val withLanding: RestDocsSettings.() -> Unit = { document.commonIncludeFiles = listOf("landing.adoc") }

private const val TOC_TITLE =
    ":toc-title: pass:[<a class=\"restdocs-nav\" href=\"index.html\">&#8592; Back to index</a><br>Table of Contents]"

/** 테스트 클래스를 JUnit 규칙([JUnitDocumentOutline])으로 읽어 [AsciiDocumentWriter] 로 문서를 쓴다. */
private class JUnitWriter(
    generator: DocumentGenerator,
    private val extractor: SnippetFileListExtractor,
    directory: File,
) {
    private val writer = AsciiDocumentWriter(generator, extractor, directory)

    fun write(
        clazz: Class<*>,
        excludedMethods: Set<String> = emptySet(),
        invocationTitles: Map<String, String> = emptyMap(),
    ): Boolean = writer.write(JUnitDocumentOutline(extractor).of(clazz, excludedMethods, invocationTitles))
}

class AsciiDocumentWriterTest :
    BehaviorSpec({
        given("스니펫이 일부 테스트에만 있을 때") {
            val snippets = Files.createTempDirectory("snippets")
            val output = Files.createTempDirectory("asciidoc")

            snippets.snippet(
                "upload",
                "http-response.adoc",
                "custom.adoc",
                "request-part-file-fields.adoc",
                "curl-request.adoc",
                "api-summary.adoc",
                "response-body.adoc",
                "error-responses.adoc",
            )

            snippets.snippet("inherited-read", "http-request.adoc")
            snippets.snippet("by-parameter/1", "http-request.adoc")
            snippets.snippet("by-parameter/2", "http-request.adoc")
            snippets.snippet("repeated", "http-request.adoc")
            snippets.snippet("공백 있는 메서드 (백틱)", "http-request.adoc")

            val writer =
                JUnitWriter(
                    DocumentGenerator("{snippets}"),
                    SnippetFileListExtractor(snippets.toString()),
                    output.toFile(),
                )

            `when`("문서를 쓰면") {
                val invocations = mapOf("byParameter/1" to "[1] a")

                RestDocsConfiguration.withSettings(withLanding) {
                    writer.write(SampleDocTest::class.java, invocationTitles = invocations)
                } shouldBe true

                val document = Files.readString(output.resolve("sample-doc-test.adoc"))

                then("제목에 AsciiDescription 괄호 옵션이 반영된다") {
                    document shouldContain "= 샘플 API [설명]"
                }

                then("스니펫이 없는 테스트는 섹션이 없고, 상속된 테스트는 포함된다") {
                    document shouldNotContain "[[undocumented]]"
                    document shouldContain "[[inherited-read]]\n== icon:link[] 상속된 조회"
                }

                then("@ParameterizedTest 는 실행마다 섹션이고, 제목 뒤에 실행 표시 이름이 붙는다") {
                    document shouldContain "[[by-parameter-1]]\n== icon:link[] by-parameter [1] a"
                    document shouldContain "[[by-parameter-2]]\n== icon:link[] by-parameter [2]"
                    document shouldContain "include::{snippets}/sample-doc-test/by-parameter/2/http-request.adoc[]"
                }

                then("@RepeatedTest 는 실행마다 같은 섹션 하나다") {
                    document shouldContain "[[repeated]]"
                }

                then("백틱 메서드 이름은 앵커로 쓸 수 있게 다듬고 스니펫 경로는 그대로 둔다") {
                    document shouldContain "[[공백-있는-메서드-백틱]]\n== icon:link[] 공백 메서드"
                    document shouldContain "include::{snippets}/sample-doc-test/공백 있는 메서드 (백틱)/http-request.adoc[]"
                }

                then("제외한 메서드(실패한 테스트)는 섹션을 만들지 않는다") {
                    val partial = Files.createTempDirectory("asciidoc")

                    JUnitWriter(
                        DocumentGenerator("{snippets}"),
                        SnippetFileListExtractor(snippets.toString()),
                        partial.toFile(),
                    ).write(SampleDocTest::class.java, excludedMethods = setOf("upload"))

                    Files.readString(partial.resolve("sample-doc-test.adoc")) shouldNotContain "[[upload]]"
                }

                then("스니펫은 정해진 순서로, 사용자 정의 스니펫은 마지막에 들어간다") {
                    val upload = document.substringAfter("[[upload]]")
                    val order =
                        listOf(
                            "api-summary.adoc",
                            "curl-request.adoc",
                            "request-part-file-fields.adoc",
                            "http-response.adoc",
                            "error-responses.adoc",
                            "custom.adoc",
                        )

                    val positions = order.map { upload.indexOf(it) }

                    positions shouldBe positions.sorted()
                    upload shouldContain ".Request Part Fields (file)"
                    upload shouldContain ".custom\ninclude::"
                    upload shouldContain ".Error Responses\ninclude::"
                }

                then("기본에서 빠진 스니펫은 넣지 않는다") {
                    document shouldNotContain "response-body.adoc"
                }

                then("블록 사이는 빈 줄 하나다") {
                    document shouldNotContain "\n\n\n"
                    document shouldContain "include::landing.adoc[]\n\n[[by-parameter-1]]"
                }

                then("처음과 끝에 index 로 돌아가는 링크가 있다") {
                    val link = "[.restdocs-nav]\nlink:index.html[&#8592; Back to index]"

                    document shouldContain ":sectlinks:\n$TOC_TITLE\n\n$link\n\ninclude::landing.adoc[]"
                    document.trimEnd().endsWith(link) shouldBe true
                }

                then("왼쪽 목차 제목 자리에도 같은 대상·글자로 링크가 있다") {
                    document shouldContain TOC_TITLE
                }
            }
        }

        given("index 링크를 끄면") {
            val output = Files.createTempDirectory("asciidoc")
            val snippets =
                Files.createTempDirectory("snippets")
                    .also { it.snippet("upload", "http-request.adoc") }

            then("링크가 없다") {
                RestDocsConfiguration.withSettings({ document.indexLink = null }) {
                    JUnitWriter(
                        DocumentGenerator("{snippets}"),
                        SnippetFileListExtractor(snippets.toString()),
                        output.toFile(),
                    ).write(SampleDocTest::class.java)
                }

                Files.readString(output.resolve("sample-doc-test.adoc")) shouldNotContain "restdocs-nav"
            }
        }

        given("목차를 쓰지 않는 설정") {
            val output = Files.createTempDirectory("asciidoc")
            val snippets =
                Files.createTempDirectory("snippets")
                    .also { it.snippet("upload", "http-request.adoc") }

            then("목차 링크는 넣지 않고 본문 링크는 남긴다") {
                JUnitWriter(
                    object : DocumentGenerator("{snippets}") {
                        override fun config(): Config = Config(toc = "")
                    },
                    SnippetFileListExtractor(snippets.toString()),
                    output.toFile(),
                ).write(SampleDocTest::class.java)

                val document = Files.readString(output.resolve("sample-doc-test.adoc"))

                document shouldNotContain ":toc-title:"
                document shouldContain "[.restdocs-nav]"
            }
        }

        given("index 링크 글자에 ] 나 줄바꿈이 있을 때") {
            then("본문 링크도 목차 링크처럼 ] 를 문자 참조로 바꾸고 줄바꿈은 공백으로 바꾼다 (링크가 끊기지 않는다)") {
                val generator = DocumentGenerator("{snippets}")
                val (navigation, toc) = RestDocsConfiguration.withSettings({
                    document.indexLinkText = "[메인]\n<목록> & 끝"
                }) {
                    generator.navigationPart() to generator.tocTitleAttribute()
                }
                val text = "&#91;메인&#93; &lt;목록&gt; &amp; 끝".replace("&#91;", "[")

                navigation shouldBe "[.restdocs-nav]\nlink:index.html[&#8592; $text]"
                toc shouldContain "&#8592; $text</a>"
                toc shouldNotContain "\n"
            }
        }

        given("language 와 index 링크 글자") {
            val generator = DocumentGenerator("{snippets}")

            then("ko 면 기본 글자가 한국어다") {
                val navigation = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    generator.navigationPart()
                }

                navigation shouldBe "[.restdocs-nav]\nlink:index.html[&#8592; 메인으로 돌아가기]"
            }

            then("정한 글자는 기본 문구와 같아도 언어와 상관없이 그대로 쓴다") {
                val navigation = RestDocsConfiguration.withSettings({
                    document.indexLinkText = "Back to index"
                    language = DocsLanguage.KO
                }) {
                    generator.navigationPart()
                }

                navigation shouldBe "[.restdocs-nav]\nlink:index.html[&#8592; Back to index]"
            }
        }

        given("하위 클래스에서 include 줄을 직접 만들 때") {
            then("document.includePrefix 를 읽을 수 있다") {
                val generator = object : DocumentGenerator("/abs/snippets") {
                    fun include(file: String) = "include::$snippetsIncludePrefix/$file[]"
                }

                generator.include("a/http-request.adoc") shouldBe "include::/abs/snippets/a/http-request.adoc[]"
            }
        }

        given("@AsciiDescription 값이 비어 있거나 공백뿐일 때 (autoBracket 기본값 true)") {
            val output = Files.createTempDirectory("asciidoc")
            val snippets = Files.createTempDirectory("snippets")
            val dir = snippets.resolve("blank-description-doc-test/blank")

            Files.createDirectories(dir)
            Files.writeString(dir.resolve("http-request.adoc"), "")

            `when`("문서를 쓰면") {
                JUnitWriter(
                    DocumentGenerator("{snippets}"),
                    SnippetFileListExtractor(snippets.toString()),
                    output.toFile(),
                ).write(BlankDescriptionDocTest::class.java)

                val document = Files.readString(output.resolve("blank-description-doc-test.adoc"))

                then("문서 제목과 섹션 제목에 빈 괄호가 붙지 않는다") {
                    document shouldContain "= 빈 설명 API\n"
                    document shouldContain "== icon:link[] 공백 설명\n"
                    document shouldNotContain "()"
                }
            }
        }

        given("문서화된 테스트가 없을 때") {
            val output = Files.createTempDirectory("asciidoc")
            val writer = JUnitWriter(
                DocumentGenerator("{snippets}"),
                SnippetFileListExtractor(Files.createTempDirectory("snippets").toString()),
                output.toFile(),
            )

            then("파일을 만들지 않는다") {
                writer.write(EmptyDocTest::class.java) shouldBe false
                Files.exists(output.resolve("empty-doc-test.adoc")) shouldBe false
            }
        }
    })
