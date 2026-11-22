@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.internal.output

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.github.kardx.junit.JUnitDocumentOutline
import io.github.kardx.lifecycle.AsciiDocumentWriter
import io.github.kardx.lifecycle.RestDocsExtensionApi
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.restdocs.RestDocumentationContextProvider
import java.nio.file.Files
import java.nio.file.Path

@Disabled("문서 생성 대상 픽스처")
@DisplayName("중첩 API")
class NestingDocTest {
    @Test
    @DisplayName("바깥")
    fun outer() = Unit

    @Test
    @DisplayName("인자 없는 오버로드")
    fun overloaded() = Unit

    @Test
    @DisplayName("인자 있는 오버로드")
    fun overloaded(provider: RestDocumentationContextProvider) = check(provider.hashCode() != 0)

    @Nested
    @DisplayName("성공")
    inner class Success {
        @Test
        @DisplayName("조회")
        fun read() = Unit

        @Nested
        inner class Deeper {
            @ParameterizedTest
            @ValueSource(longs = [1, 2])
            fun byId(id: Long) = check(id > 0)
        }
    }
}

private fun Path.snippet(method: String) {
    val dir = resolve("nesting-doc-test").resolve(method)

    Files.createDirectories(dir)
    Files.writeString(dir.resolve("http-request.adoc"), "")
}

class SnippetNamesTest :
    BehaviorSpec({
        val read = NestingDocTest.Success::class.java.getMethod("read")
        val byId = NestingDocTest.Success.Deeper::class.java.getMethod("byId", Long::class.javaPrimitiveType)
        val withProvider =
            NestingDocTest::class.java.getMethod("overloaded", RestDocumentationContextProvider::class.java)

        given("@Nested 테스트") {
            then("가장 바깥 클래스의 문서에 들어가고, 메서드 자리 앞에 안쪽 클래스 이름이 붙는다") {
                SnippetNames.documentClass(NestingDocTest.Success.Deeper::class.java) shouldBe
                    NestingDocTest::class.java
                SnippetNames.baseName(NestingDocTest.Success::class.java, read) shouldBe "success-read"
                SnippetPaths.relativeDirectory(NestingDocTest::class.java, "success-read") shouldBe
                    "nesting-doc-test/success-read"
            }
        }

        given("@ParameterizedTest 실행") {
            then("실행 번호가 하위 디렉터리가 된다") {
                val target = SnippetNames.target(
                    NestingDocTest.Success.Deeper::class.java,
                    byId,
                    "[engine:junit-jupiter]/[class:x]/[test-template:byId(long)]/[test-template-invocation:#2]",
                )

                target.methodName shouldBe "success-deeper-byId/2"
                SnippetPaths.relativeDirectory(target.documentClass, target.methodName) shouldBe
                    "nesting-doc-test/success-deeper-by-id/2"
            }
        }

        given("이름이 같은 오버로드 테스트 메서드") {
            then("인자가 있는 쪽에 인자 타입 이름을 붙이고, 인자 없는 쪽은 이름 그대로다") {
                val withoutArguments = NestingDocTest::class.java.getMethod("overloaded")

                SnippetNames.baseName(NestingDocTest::class.java, withProvider) shouldBe
                    "overloaded-restDocumentationContextProvider"
                SnippetNames.baseName(NestingDocTest::class.java, withoutArguments) shouldBe "overloaded"
            }
        }

        given("바깥 클래스 문서") {
            val snippets = Files.createTempDirectory("snippets")
            val output = Files.createTempDirectory("asciidoc")

            listOf(
                "outer",
                "overloaded",
                "overloaded-rest-documentation-context-provider",
                "success-read",
                "success-deeper-by-id/1",
                "success-deeper-by-id/2",
            ).forEach { snippets.snippet(it) }

            val extractor = SnippetFileListExtractor(snippets.toString())
            val outline = JUnitDocumentOutline(extractor).of(
                NestingDocTest::class.java,
                invocationTitles = mapOf("success-deeper-byId/2" to "[2] 2"),
            )

            val writer = AsciiDocumentWriter(DocumentGenerator("{snippets}"), extractor, output.toFile())

            writer.write(outline) shouldBe true

            val document = Files.readString(output.resolve("nesting-doc-test.adoc"))

            then("@Nested 테스트도 섹션이 되고 제목 앞에 @Nested 클래스 표시 이름이 붙는다") {
                document shouldContain "[[success-read]]\n== icon:link[] 성공 - 조회"
                document shouldContain "[[success-deeper-by-id-2]]\n== icon:link[] 성공 - Deeper - by-id [2] 2"
                document shouldContain "include::{snippets}/nesting-doc-test/success-deeper-by-id/1/http-request.adoc[]"
            }

            then("오버로드는 섹션이 따로 있다") {
                document shouldContain "== icon:link[] 인자 없는 오버로드"
                document shouldContain "[[overloaded-rest-documentation-context-provider]]\n== icon:link[] 인자 있는 오버로드"
            }

            then("@Nested 클래스는 문서를 따로 만들지 않는다") {
                Files.exists(output.resolve("success.adoc")) shouldBe false
                document shouldNotContain "[[read]]"
            }
        }
    })
