package io.github.kardx.kotest.fixture

import io.github.kardx.annotation.AsciiDescription
import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.kotest.EnumDocumentationExtension
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.restDocsMockMvc
import io.github.kardx.websocket.MessageDocumentation
import io.kotest.core.annotation.DisplayName
import io.kotest.core.annotation.EnabledIf
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.FunSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@EnabledIf(LaunchedOnly::class)
@DisplayName("행동 문서")
@AsciiDescription("BehaviorSpec")
class BehaviorDocsSpec :
    BehaviorSpec({
        val restDocs = extension(RestDocsExtension())
        val mockMvc = helloMockMvc(restDocs.provider)

        given("사용자가 있을 때") {
            `when`("단건 조회하면") {
                then("사용자 정보를 돌려준다") {
                    mockMvc.perform(get("/hello/{id}", 1))
                        .andDocument { description = "단건 조회" }
                }

                then("실패한 테스트") {
                    mockMvc.perform(get("/hello/{id}", 2))
                        .andDocument { }

                    error("문서화한 뒤 실패한다")
                }

                then("문서화하지 않는 테스트") {
                    FixtureLaunch.record("undocumented")
                }
            }

            `when`("getUser API 를 부르면") {
                then("200 OK") {
                    mockMvc.perform(get("/hello/{id}", 3))
                        .andDocument { }
                }
            }
        }
    })

@EnabledIf(LaunchedOnly::class)
class FunDocsSpec :
    FunSpec({
        val restDocs = extension(RestDocsExtension())
        val mockMvc = helloMockMvc(restDocs.provider)

        RestDocsConfiguration.configure { document.commonIncludeFiles = listOf("spec.adoc") }

        beforeEach {
            RestDocsConfiguration.configure { document.commonIncludeFiles = document.commonIncludeFiles + "each.adoc" }
        }

        afterEach {
            // 테스트가 끝날 때 한 설정도 그 테스트의 것이라 다음 테스트로 새지 않는다
            RestDocsConfiguration.configure {
                document.commonIncludeFiles =
                    document.commonIncludeFiles + "after-each.adoc"
            }
        }

        test("첫 테스트") {
            FixtureLaunch.record("first")
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }

        test("다음 테스트") {
            FixtureLaunch.record("next")
            mockMvc.perform(get("/hello/{id}", 2))
                .andDocument { }
        }

        context("이름 겹침") {
            test("같은 이름 (a)") {
                mockMvc.perform(get("/hello/{id}", 3))
                    .andDocument { }
            }

            test("같은 이름 [a]") {
                mockMvc.perform(get("/hello/{id}", 4))
                    .andDocument { }
            }
        }

        test("스레드를 옮겨도 지금 테스트를 찾는다") {
            withContext(Dispatchers.Default) {
                mockMvc.perform(get("/hello/{id}", 5))
                    .andDocument { }
            }
        }

        // 문서 설정을 기억하는 시점을 확인하므로 마지막 테스트여야 한다 (문서에는 마지막 테스트의 설정이 쓰인다)
        test("설정을 바꾸는 테스트") {
            RestDocsConfiguration.configure { document.commonIncludeFiles = document.commonIncludeFiles + "leaf.adoc" }
            FixtureLaunch.record("changing")
            mockMvc.perform(get("/hello/{id}", 6))
                .andDocument { }
        }
    })

@EnabledIf(LaunchedOnly::class)
class DescribeDocsSpec :
    DescribeSpec({
        val restDocs = extension(
            RestDocsExtension(
                sectionTitle = { test -> test.names.last() },
                snippetName = { test -> "custom " + test.names.last() },
            ),
        )
        val mockMvc = helloMockMvc(restDocs.provider)

        describe("GET /hello/{id}") {
            it("returns user") {
                mockMvc.perform(get("/hello/{id}", 1))
                    .andDocument { }
            }

            context("when the id is large") {
                it("returns user too") {
                    mockMvc.perform(get("/hello/{id}", 999))
                        .andDocument { }
                }
            }
        }
    })

/** 프로젝트 설정에 등록한 확장으로 문서화한다. 스펙에는 확장이 없다. */
@EnabledIf(LaunchedOnly::class)
class GlobalDocsSpec :
    FunSpec({
        val mockMvc = helloMockMvc(RestDocsExtension.currentTestProvider)

        test("전역 등록") {
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }

        test("다른 스레드에서 문서화") {
            val testThread = Thread.currentThread()

            withContext(Dispatchers.Default) {
                FixtureLaunch.seen["thread-switched"] = Thread.currentThread() != testThread
                mockMvc.perform(get("/hello/{id}", 2))
                    .andDocument { }
            }
        }
    })

/**
 * 프로젝트 설정과 스펙 양쪽에 등록해도 한 번만 문서화하고, 스펙에 등록한 확장의 옵션(섹션 제목, 문서 생성기)이 이긴다.
 * 문서도 스펙의 확장이 한 번만 쓴다.
 */
@EnabledIf(LaunchedOnly::class)
class DoubleRegisteredDocsSpec :
    FunSpec({
        val restDocs = extension(
            RestDocsExtension(
                sectionTitle = { test -> "스펙 제목: ${test.names.joinToString(" / ")}" },
                documentGenerator = {
                    FixtureLaunch.seen["spec-extension-wrote"] =
                        (FixtureLaunch.seen["spec-extension-wrote"] as? Int ?: 0) + 1
                    object : DocumentGenerator() {
                        override fun config() = Config(tocLevels = 1)
                    }
                },
            ),
        )
        val mockMvc = helloMockMvc(restDocs.provider)

        test("두 번 등록") {
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }
    })

@EnabledIf(LaunchedOnly::class)
class EnumDocsSpec :
    FunSpec({
        extension(EnumDocumentationExtension(packageName = "io.github.kardx.kotest.fixture.code", title = "코드"))

        test("아무것도 하지 않는다") {
            FixtureLaunch.record("enum")
        }
    })

/** 확장 인스턴스나 provider 없이 `restDocsMockMvc(context)` 로 만든 클라이언트가 지금 리프 테스트를 찾는다. */
@EnabledIf(LaunchedOnly::class)
class CurrentTestClientDocsSpec :
    FunSpec({
        extension(RestDocsExtension())

        val mockMvc = restDocsMockMvc(helloWebContext())

        test("첫 테스트") {
            mockMvc.perform(get("/hello/{id}", 1))
                .andDocument { }
        }

        test("둘째 테스트") {
            mockMvc.perform(get("/hello/{id}", 2))
                .andDocument { }
        }
    })

/** 리프 테스트 밖(스펙 본문)에서 문서화하면 알려 준다. */
@EnabledIf(LaunchedOnly::class)
class OutsideTestDocsSpec :
    FunSpec({
        val restDocs = extension(RestDocsExtension())
        val mockMvc = helloMockMvc(restDocs.provider)

        context("컨테이너") {
            FixtureLaunch.seen["outside"] = runCatching {
                mockMvc.perform(get("/hello/{id}", 1))
            }.exceptionOrNull()

            test("안쪽") {
                FixtureLaunch.record("inside")
            }
        }
    })

/** WebSocket·STOMP 메시지도 같은 provider 로 문서화한다. */
@EnabledIf(LaunchedOnly::class)
@DisplayName("메시지 문서")
class MessageDocsSpec :
    FunSpec({
        val restDocs = extension(RestDocsExtension())

        context("채팅") {
            test("메시지 보내기") {
                MessageDocumentation(restDocs.provider).send("/app/chat.send", mapOf("text" to "안녕하세요")) {
                    description = "채팅 메시지를 보낸다."
                }
            }
        }
    })
