package io.github.kardx.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.mock.env.MockEnvironment
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread

class RestDocsConfigurationTest :
    BehaviorSpec({
        given("configure") {
            then("전역 기본값을 바꾸고 reset 으로 되돌린다") {
                RestDocsConfiguration.configure { masking.replacement = "[hidden]" }

                RestDocsConfiguration.current.masking.replacement shouldBe "[hidden]"

                RestDocsConfiguration.reset()

                RestDocsConfiguration.current.masking.replacement shouldBe "****"
            }
        }

        given("예외를 던지는 configure 블록") {
            then("예외는 그대로 전파되고 이전 기본값이 남는다") {
                RestDocsConfiguration.configure { extraction.maxDepth = 5 }

                shouldThrow<IllegalArgumentException> {
                    RestDocsConfiguration.configure {
                        extraction.maxDepth = 1
                        descriptions.annotation("오타")
                    }
                }.message shouldContain "'오타'"

                RestDocsConfiguration.current.extraction.maxDepth shouldBe 5
            }

            then("등록되지 않으므로 이후의 configure 와 applyEnvironment 는 성공한다") {
                shouldThrow<IllegalArgumentException> {
                    RestDocsConfiguration.configure { descriptions.annotation("오타") }
                }

                RestDocsConfiguration.configure { masking.replacement = "[hidden]" }
                RestDocsConfiguration.applyEnvironment(
                    MockEnvironment().withProperty("kardx.max-extraction-depth", "4"),
                )

                RestDocsConfiguration.current.masking.replacement shouldBe "[hidden]"
                RestDocsConfiguration.current.extraction.maxDepth shouldBe 4
                RestDocsConfiguration.current.descriptions.annotations shouldBe emptyList()
            }
        }

        given("잘못된 속성을 읽는 applyEnvironment") {
            then("예외는 그대로 전파되고 이전 속성 값과 configure 값이 남는다") {
                RestDocsConfiguration.applyEnvironment(
                    MockEnvironment().withProperty("kardx.masked-header-value", "[yml]"),
                )

                RestDocsConfiguration.configure { extraction.maxDepth = 7 }

                val broken = MockEnvironment().withProperty("kardx.masked-header-value", "[broken]")
                    .withProperty("kardx.description-annotations", "오타")

                shouldThrow<IllegalArgumentException> { RestDocsConfiguration.applyEnvironment(broken) }
                    .message shouldContain "kardx.description-annotations"

                RestDocsConfiguration.current.masking.replacement shouldBe "[yml]"
                RestDocsConfiguration.current.extraction.maxDepth shouldBe 7

                RestDocsConfiguration.configure { http.printRequests = true }

                RestDocsConfiguration.current.http.printRequests shouldBe true
            }
        }

        given("withSettings") {
            then("블록 안에서만 적용되고 바깥 값은 그대로다") {
                RestDocsConfiguration.configure { extraction.maxDepth = 5 }

                val inside = RestDocsConfiguration.withSettings({ extraction.maxDepth = 2 }) {
                    RestDocsConfiguration.current.extraction.maxDepth
                }

                inside shouldBe 2
                RestDocsConfiguration.current.extraction.maxDepth shouldBe 5
            }

            then("중첩하면 바깥 범위 값을 이어받는다") {
                RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) {
                    RestDocsConfiguration.withSettings({ extraction.maxDepth = 3 }) {
                        RestDocsConfiguration.current.extraction.arrayElementFieldsOptional shouldBe true
                        RestDocsConfiguration.current.extraction.maxDepth shouldBe 3
                    }

                    RestDocsConfiguration.current.extraction.maxDepth shouldBe 10
                }
            }

            then("다른 스레드에는 보이지 않는다") {
                var seenByOtherThread = true

                RestDocsConfiguration.withSettings({ openApi.enabled = false }) {
                    thread { seenByOtherThread = RestDocsConfiguration.current.openApi.enabled }
                        .join()
                }

                seenByOtherThread shouldBe true
            }

            then("예외가 나도 범위 설정을 되돌린다") {
                shouldThrow<IllegalStateException> {
                    RestDocsConfiguration.withSettings({ http.printRequests = true }) { error("boom") }
                }

                RestDocsConfiguration.current.http.printRequests shouldBe false
            }
        }

        given("출력 디렉터리") {
            then("pom.xml 이 있으면 Maven, 없으면 Gradle 경로") {
                val gradle = Files.createTempDirectory("gradle").toFile()
                val maven = Files.createTempDirectory("maven").toFile()

                File(maven, "pom.xml").writeText("<project/>")

                RestDocsConfiguration.buildDirectory(gradle) shouldBe "build"
                RestDocsConfiguration.buildDirectory(maven) shouldBe "target"
            }

            then("설정하면 그 경로를 쓴다") {
                RestDocsConfiguration.configure {
                    output.snippetsDirectory = "out/snippets"
                    output.asciidocDirectory = "out/asciidoc"
                }

                RestDocsConfiguration.snippetsDirectory().path shouldBe "out/snippets"
                RestDocsConfiguration.asciidocDirectory().path shouldBe "out/asciidoc"
            }
        }

        given("ResponseWrapper") {
            then("dataPath 기본값은 data, 빈 값은 거부한다") {
                ResponseWrapper(Any::class).dataPath shouldBe "data"
                shouldThrow<IllegalArgumentException> { ResponseWrapper(Any::class, " ") }
            }
        }
    })
