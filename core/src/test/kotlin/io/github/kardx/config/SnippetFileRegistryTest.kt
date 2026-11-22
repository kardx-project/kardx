package io.github.kardx.config

import io.github.kardx.internal.asciidoc.SnippetFileListExtractor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class SnippetFileRegistryTest :
    BehaviorSpec({
        given("라이브러리 스니펫 종류") {
            then("선언 순서대로 BUILT_IN 에 있고, 이름으로 찾는다") {
                SnippetFile.BUILT_IN.first() shouldBe SnippetFile.API_SUMMARY
                SnippetFile.builtInNamed("response-body") shouldBe SnippetFile.RESPONSE_BODY
                SnippetFile.defaults().contains(SnippetFile.HTTPIE_INFO) shouldBe false
            }

            then("라이브러리 종류와 같은 이름의 사용자 종류는 만들 수 없다") {
                shouldThrow<IllegalArgumentException> { SnippetFile.custom("http-request", "Request") }
                shouldThrow<IllegalArgumentException> { SnippetFile.custom("request-fields-x", "x") }
            }
        }

        given("사용자 스니펫 종류를 응답 필드 뒤에 등록하면") {
            val audit = SnippetFile.custom("audit-log", title = "Audit Log")
            val hidden = SnippetFile.custom("debug-dump", title = "Debug", includedByDefault = false)
            val directory = Files.createTempDirectory("snippets")
            val method = Files.createDirectories(directory.resolve("doc/method"))

            listOf("http-response.adoc", "response-fields.adoc", "links.adoc", "audit-log.adoc", "debug-dump.adoc")
                .forEach { Files.writeString(method.resolve(it), "x") }

            val includes = RestDocsConfiguration.withSettings({
                document {
                    register(audit, after = SnippetFile.RESPONSE_FIELDS)
                    register(hidden)
                }
            }) {
                SnippetFileListExtractor(directory.toString()).extract("doc/method")
            }

            then("그 자리에 소제목과 함께 들어가고, 기본 제외 종류는 snippets 에 넣어야 들어간다") {
                includes.map { it.fileName } shouldContainExactly
                    listOf("http-response.adoc", "response-fields.adoc", "audit-log.adoc", "links.adoc")
                includes.single { it.kind == audit }.title shouldBe "Audit Log"
            }

            then("withSettings 밖의 설정에는 등록이 남지 않는다") {
                RestDocsConfiguration.current.document.order shouldBe SnippetFile.BUILT_IN
            }

            then("before 로 앞에 끼울 수 있고, after 와 before 를 같이 주면 거부한다") {
                val settings = RestDocsSettings().apply { document { register(audit, before = SnippetFile.CURL_INFO) } }

                settings.document.order.indexOf(audit) shouldBe
                    SnippetFile.BUILT_IN.indexOf(SnippetFile.CURL_INFO)
                shouldThrow<IllegalArgumentException> {
                    RestDocsSettings().document.register(audit, after = SnippetFile.LINKS, before = SnippetFile.LINKS)
                }
            }
        }
    })
