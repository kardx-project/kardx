@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.internal.asciidoc

import io.github.kardx.asciidoc.AsciiDocEnumDocumentFormat
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.lifecycle.EnumCodeDocumentation
import io.github.kardx.lifecycle.RestDocsExtensionApi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import java.nio.file.Files

class EnumCodeDocumentTest :
    BehaviorSpec({
        fun write(packageName: String): String {
            val output = Files.createTempDirectory("enum").toFile()

            EnumCodeDocument(AsciiDocEnumDocumentFormat(), output.path).write(packageName, "codes", "코드")

            return File(output, "codes.adoc").readText()
        }

        given("@EnumCode Enum 이 있는 패키지") {
            val document = write("io.github.kardx.internal.asciidoc.fixture")

            then("하위 패키지까지 스캔해 Enum 마다 Code / Description 표를 만든다") {
                document shouldContain "= 코드"
                document shouldContain "[[회원_등급]]\n== 회원 등급\nenum : MemberGrade"
                document shouldContain "| Code | Description"
                document shouldContain "== 하위 패키지 코드"
            }

            then("코드 기본값은 JSON 값, 설명 기본값은 description 프로퍼티다") {
                document shouldContain "| GOLD\n| 골드"
                document shouldNotContain "| G\n"
            }

            then("코드 기본값은 @JsonValue 값을 따른다") {
                document shouldContain "| ready\n| 준비"
                document shouldContain "| shipped\n| 발송"
            }

            then("코드 기본값은 상수의 @JsonProperty 값을 따르고, 설명 프로퍼티가 없으면 상수 이름을 쓴다") {
                document shouldContain "| asc\n| ASC"
                document shouldContain "| desc\n| DESC"
                document shouldContain "| X\n| X"
            }

            then("설정이 기본값(description)이면 description 프로퍼티를 쓴다") {
                document shouldContain "| ON\n| 설명-켜짐"
            }

            then("직접 지정한 프로퍼티는 필드와 getter 에서 읽는다") {
                document shouldContain "| 10\n| 결제 완료"
            }

            then("codeProperty = \"name\" 은 상수 이름이다 (@JsonValue 무시)") {
                document shouldContain "| FIRST\n| FIRST"
            }

            then("@Deprecated 상수와 @EnumCode 없는 Enum 은 뺀다") {
                document shouldNotContain "| 20"
                document shouldNotContain "| 완료"
                document shouldNotContain "NotDocumented"
            }
        }

        given("AsciiDoc 출력 디렉터리 아래 code/ 에 쓰면") {
            val root = Files.createTempDirectory("asciidoc").toFile()
            val document = RestDocsConfiguration.withSettings({ output.asciidocDirectory = root.path }) {
                val code = File(root, "code").apply { mkdirs() }

                EnumCodeDocument(AsciiDocEnumDocumentFormat(), code.path)
                    .write("io.github.kardx.internal.asciidoc.fixture", "codes", "코드")

                File(code, "codes.adoc").readText()
            }

            then("왼쪽 목차 제목 자리에 한 단계 위 index 로 가는 링크가 있다") {
                document shouldContain ":toc-title: pass:[<a class=\"restdocs-nav\" href=\"../index.html\">"
            }
        }

        given("출력 디렉터리를 AsciiDoc 출력 디렉터리 아래의 상대 경로로 주면 (EnumDocumentationExtension(outputDirectory = ...))") {
            // 작업 디렉터리 기준 상대 경로. AsciiDoc 디렉터리도 상대 경로로 정해 둘을 같은 기준으로 비교하는지 본다
            val base = "build/tmp/enum-index-link-${System.nanoTime()}"
            val document = RestDocsConfiguration.withSettings({ output.asciidocDirectory = "$base/asciidoc" }) {
                val directory = EnumCodeDocumentation.directory("$base/asciidoc/reference/code")

                File(directory).mkdirs()
                EnumCodeDocumentation.write(
                    "io.github.kardx.internal.asciidoc.fixture",
                    "codes",
                    "코드",
                    directory,
                    AsciiDocEnumDocumentFormat(),
                )

                File(directory, "codes.adoc").readText()
            }

            then("두 단계 위 index 로 가는 링크(../../index.html)가 목차 제목 자리에 있다") {
                document shouldContain ":toc-title: pass:[<a class=\"restdocs-nav\" href=\"../../index.html\">"
            }
        }

        given("index 링크를 끄거나 AsciiDoc 출력 디렉터리 밖에 쓰면") {
            then("목차 링크를 넣지 않는다") {
                RestDocsConfiguration.withSettings({ document.indexLink = null }) {
                    write("io.github.kardx.internal.asciidoc.fixture")
                } shouldNotContain ":toc-title:"
                write("io.github.kardx.internal.asciidoc.fixture") shouldNotContain ":toc-title:"
            }
        }

        given("설정 extraction.enumDescriptionProperty 가 label 일 때") {
            val document = RestDocsConfiguration.withSettings({ extraction.enumDescriptionProperty = "label" }) {
                write("io.github.kardx.internal.asciidoc.fixture")
            }

            then("설명 기본값은 그 프로퍼티가 description 보다 먼저다") {
                document shouldContain "| ON\n| 켜짐"
            }

            then("그 프로퍼티가 없는 Enum 은 description 으로 대체한다") {
                document shouldContain "| GOLD\n| 골드"
            }
        }

        given("descriptionProperty 에 Enum 에 없는 프로퍼티를 적으면") {
            then("Enum 과 속성 이름을 담은 예외로 실패한다") {
                val error =
                    shouldThrow<IllegalArgumentException> { write("io.github.kardx.internal.asciidoc.invalid") }

                error.message shouldContain "descriptionProperty = \"descripton\""
                error.message shouldContain "io.github.kardx.internal.asciidoc.invalid.TypoStatus"
            }
        }

        given("codeProperty 에 Enum 에 없는 프로퍼티를 적으면") {
            then("Enum 과 속성 이름을 담은 예외로 실패한다") {
                val error =
                    shouldThrow<IllegalArgumentException> { write("io.github.kardx.internal.asciidoc.invalidcode") }

                error.message shouldContain "codeProperty = \"cod\""
                error.message shouldContain "io.github.kardx.internal.asciidoc.invalidcode.CodeTypo"
            }
        }

        given("제목에 / 가 있고 같은 제목이 둘일 때") {
            val document = write("io.github.kardx.internal.asciidoc.titles")

            then("/ 는 공백을 둔 - 로 읽을 수 있게 바꾼다") {
                document shouldContain "[[입금_-_출금]]\n== 입금 - 출금\n"
                document shouldNotContain "입금or출금"
            }

            then("겹치는 제목의 접미사에도 괄호를 쓰지 않는다") {
                document shouldContain "== 입금 - 출금 - "
                document shouldNotContain "(Transfer"
            }
        }
    })
