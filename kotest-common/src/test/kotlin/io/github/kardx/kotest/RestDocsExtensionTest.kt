package io.github.kardx.kotest

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.kotest.fixture.BehaviorDocsSpec
import io.github.kardx.kotest.fixture.CurrentTestClientDocsSpec
import io.github.kardx.kotest.fixture.DescribeDocsSpec
import io.github.kardx.kotest.fixture.DoubleRegisteredDocsSpec
import io.github.kardx.kotest.fixture.EnumDocsSpec
import io.github.kardx.kotest.fixture.FixtureLaunch
import io.github.kardx.kotest.fixture.FunDocsSpec
import io.github.kardx.kotest.fixture.GlobalDocsSpec
import io.github.kardx.kotest.fixture.MessageDocsSpec
import io.github.kardx.kotest.fixture.OutsideTestDocsSpec
import io.github.kardx.kotest.fixture.isolationFixtures
import io.github.kardx.kotest.fixture.projectConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File

class RestDocsExtensionTest :
    BehaviorSpec({
        given("BehaviorSpec 스펙") {
            val run = runFixtures(BehaviorDocsSpec::class)
            val document = run.document("behavior-docs-spec")
                .readText()

            then("given / when / then 이름으로 스니펫 디렉터리를 만든다 (스타일 접두사 없이, 한글 그대로)") {
                run.snippet("behavior-docs-spec", "사용자가-있을-때-단건-조회하면-사용자-정보를-돌려준다/http-request.adoc")
                    .isFile
                    .shouldBeTrue()
                run.snippet("behavior-docs-spec", "사용자가-있을-때-get-user-api-를-부르면-200-ok/http-request.adoc")
                    .isFile
                    .shouldBeTrue()
            }

            then("스펙 클래스의 Kotest @DisplayName 과 @AsciiDescription 이 문서 제목이 된다") {
                document shouldContain "= 행동 문서 (BehaviorSpec)\n"
            }

            then("섹션 제목은 테스트 이름을 ' - ' 로 잇고, 실행 순서대로 둔다") {
                val first = section(
                    "사용자가-있을-때-단건-조회하면-사용자-정보를-돌려준다",
                    "사용자가 있을 때 - 단건 조회하면 - 사용자 정보를 돌려준다",
                )
                val second = section(
                    "사용자가-있을-때-get-user-api-를-부르면-200-ok",
                    "사용자가 있을 때 - getUser API 를 부르면 - 200 OK",
                )

                document shouldContain first
                document shouldContain second
                (document.indexOf(first) < document.indexOf(second)).shouldBeTrue()
            }

            then("실패한 테스트의 스니펫은 지우고 문서에서 뺀다") {
                run.results["실패한 테스트"].shouldNotBeNull().isErrorOrFailure.shouldBeTrue()
                run.snippet("behavior-docs-spec", "사용자가-있을-때-단건-조회하면-실패한-테스트").exists().shouldBeFalse()
                document shouldNotContain "실패한 테스트"
            }

            then("문서화하지 않은 테스트(스니펫 없음)는 문서에 넣지 않는다") {
                run.results["문서화하지 않는 테스트"].shouldNotBeNull().isSuccess.shouldBeTrue()
                document shouldNotContain "문서화하지 않는 테스트"
            }

            then("컨테이너(given, when)는 섹션이 되지 않는다") {
                document shouldNotContain "== icon:link[] 사용자가 있을 때\n"
                document shouldNotContain "[[사용자가-있을-때]]"
            }
        }

        given("FunSpec 스펙") {
            FixtureLaunch.seen.clear()

            val run = runFixtures(FunDocsSpec::class)
            val document = run.document("fun-docs-spec")
                .readText()

            then("문서 제목은 @DisplayName 이 없으면 클래스 단순 이름이다") {
                document shouldContain "= FunDocsSpec\n"
            }

            then("테스트 안과 beforeEach·afterEach 에서 한 configure 는 테스트가 끝나면 되돌리고, 스펙 본문의 설정은 유지한다") {
                FixtureLaunch.seen["first"] shouldBe listOf("spec.adoc", "each.adoc")
                FixtureLaunch.seen["next"] shouldBe listOf("spec.adoc", "each.adoc")
                FixtureLaunch.seen["changing"] shouldBe listOf("spec.adoc", "each.adoc", "leaf.adoc")
            }

            then("문서 설정은 beforeEach 가 끝나고 본문이 시작하기 전에 기억한다 (마지막 테스트의 본문에서 한 설정은 빠진다)") {
                document shouldContain "include::spec.adoc[]\ninclude::each.adoc[]"
                document shouldNotContain "leaf.adoc"
                document shouldNotContain "after-each.adoc"
            }

            then("같은 스니펫 이름이 되는 테스트는 뒤의 것에 번호를 붙인다") {
                document shouldContain section("이름-겹침-같은-이름-a", "이름 겹침 - 같은 이름 (a)")
                document shouldContain section("이름-겹침-같은-이름-a-2", "이름 겹침 - 같은 이름 [a]")
                run.snippet("fun-docs-spec", "이름-겹침-같은-이름-a-2/http-request.adoc").isFile.shouldBeTrue()
            }

            then("코루틴이 다른 스레드로 옮겨 가도 지금 테스트의 자리에 스니펫을 쓴다") {
                run.snippet("fun-docs-spec", "스레드를-옮겨도-지금-테스트를-찾는다/http-request.adoc").isFile.shouldBeTrue()
            }
        }

        given("DescribeSpec 스펙과 생성자로 바꾼 이름 규칙") {
            val run = runFixtures(DescribeDocsSpec::class)
            val document = run.document("describe-docs-spec")
                .readText()

            then("섹션 제목과 스니펫 이름을 람다로 정한다 (스니펫 이름도 경로에 쓸 수 있게 다듬는다)") {
                document shouldContain section("custom-returns-user", "returns user")
                document shouldContain section("custom-returns-user-too", "returns user too")
                run.snippet("describe-docs-spec", "custom-returns-user/http-request.adoc").isFile.shouldBeTrue()
            }
        }

        given("격리 모드 스펙 (6.x: InstancePerRoot·InstancePerLeaf·InstancePerTest, 5.x: InstancePerLeaf·InstancePerTest)") {
            FixtureLaunch.seen.clear()

            val run = runFixtures(*isolationFixtures.values.toTypedArray())

            isolationFixtures.keys.forEach { mode ->
                val spec = "$mode-isolation-docs-spec"

                then("$mode: 인스턴스가 여럿이어도 모든 리프 테스트가 한 문서에 한 번씩, 실행 순서대로 들어간다") {
                    val document = run.document(spec)
                        .readText()
                    val anchors = Regex("""\[\[([^]]+)]]""").findAll(document)
                        .map { it.groupValues[1] }
                        .toList()

                    anchors shouldContainExactly listOf("루트", "묶음-첫째", "묶음-둘째")
                    document shouldContain section("묶음-첫째", "묶음 - 첫째")
                }

                then("$mode: 스니펫 디렉터리 이름이 테스트 경로를 따른다 (번호가 붙지 않는다)") {
                    File(run.snippets, spec).list()
                        .orEmpty()
                        .sorted() shouldContainExactly listOf("루트", "묶음-둘째", "묶음-첫째")
                }

                then("$mode: beforeEach 에서 한 설정은 테스트마다 한 번만 적용되고, 문서 include 도 한 번이다") {
                    listOf("root", "first", "second").forEach { test ->
                        FixtureLaunch.seen["$mode-$test"] shouldBe listOf("each.adoc")
                    }
                    run.document(spec)
                        .readText()
                        .split("include::each.adoc[]").size shouldBe 2
                }
            }
        }

        given("프로젝트 설정에 등록한 확장") {
            FixtureLaunch.seen.clear()

            val config = projectConfig(RestDocsExtension())
            val run = runFixtures(GlobalDocsSpec::class, DoubleRegisteredDocsSpec::class, config = config)

            then("스펙에 확장이 없어도 currentTestProvider 로 문서화한다") {
                run.document("global-docs-spec")
                    .readText() shouldContain section("전역-등록", "전역 등록")
            }

            then("코루틴이 다른 스레드로 옮겨 가도 currentTestProvider 가 지금 테스트를 찾는다") {
                FixtureLaunch.seen["thread-switched"] shouldBe true
                run.results["다른 스레드에서 문서화"].shouldNotBeNull().isSuccess.shouldBeTrue()
                run.snippet("global-docs-spec", "다른-스레드에서-문서화/http-request.adoc").isFile.shouldBeTrue()
            }

            then("스펙에도 등록했으면 한 번만 문서화한다") {
                val document = run.document("double-registered-docs-spec")
                    .readText()

                document.split("[[두-번-등록]]").size shouldBe 2
                run.snippet("double-registered-docs-spec", "두-번-등록/http-request.adoc").isFile.shouldBeTrue()
            }

            then("스펙과 프로젝트 양쪽에 등록하면 스펙 확장의 섹션 제목과 문서 생성기를 쓰고 문서는 한 번만 쓴다") {
                val document = run.document("double-registered-docs-spec")
                    .readText()

                document shouldContain "스펙 제목: 두 번 등록"
                document shouldContain ":toclevels: 1"
                document shouldNotContain ":toclevels: 3"
                FixtureLaunch.seen["spec-extension-wrote"] shouldBe 1
            }

            then("프로젝트 확장은 스펙에 확장이 없는 스펙을 그대로 문서화한다") {
                run.document("global-docs-spec")
                    .readText() shouldContain ":toclevels: 3"
            }
        }

        given("WebSocket·STOMP 메시지 문서화 (MessageDocumentation)") {
            val run = runFixtures(MessageDocsSpec::class)
            val directory = "채팅-메시지-보내기"

            then("같은 provider 로 메시지 스니펫을 만들고 문서 섹션이 된다") {
                run.snippet("message-docs-spec", "$directory/message-01.adoc").isFile.shouldBeTrue()
                run.document("message-docs-spec")
                    .readText() shouldContain section(directory, "채팅 - 메시지 보내기")
            }

            then("AsyncAPI 요약과 태그는 섹션 제목과 스펙 제목이다 (테스트 메서드가 없어도)") {
                val metadata = run.snippet("message-docs-spec", "$directory/message-01.asyncapi.json")
                    .readText()

                metadata shouldContain "\"summary\":\"채팅 - 메시지 보내기\""
                metadata shouldContain "\"tag\":\"메시지 문서\""
            }
        }

        given("provider 없이 restDocsMockMvc(context) 로 만든 클라이언트") {
            val run = runFixtures(CurrentTestClientDocsSpec::class)

            then("요청마다 그때 실행 중인 리프 테스트의 스니펫 자리에 문서화한다") {
                run.snippet("current-test-client-docs-spec", "첫-테스트/http-request.adoc").isFile.shouldBeTrue()
                run.snippet("current-test-client-docs-spec", "둘째-테스트/http-request.adoc").isFile.shouldBeTrue()
                run.document("current-test-client-docs-spec")
                    .readText() shouldContain section("둘째-테스트", "둘째 테스트")
            }
        }

        given("리프 테스트 밖에서 문서화하면") {
            FixtureLaunch.seen.clear()

            val run = runFixtures(OutsideTestDocsSpec::class)

            then("어디서 문서화해야 하는지 알려 준다") {
                FixtureLaunch.seen["outside"].shouldBeInstanceOf<IllegalStateException>()
                    .message shouldContain "kardx[KDX-401]: no Kotest leaf test is running"
                run.document("outside-test-docs-spec").exists().shouldBeFalse()
            }
        }

        given("EnumDocumentationExtension") {
            val run = runFixtures(EnumDocsSpec::class)

            then("@EnumCode Enum 의 코드 문서를 AsciiDoc 출력 디렉터리 아래 code 에 쓴다") {
                val document = run.document("code/enum-code")
                    .readText()

                document shouldContain "= 코드"
                document shouldContain "ACTIVE"
                document shouldContain "사용 중"
            }
        }

        given("Kotest Spring 확장이 클래스패스에 없을 때") {
            then("Spring 연동 없이 코드 설정만으로 문서를 만든다 (이 모듈의 테스트는 Spring 확장 없이 돈다)") {
                shouldThrow<ClassNotFoundException> {
                    Class.forName("io.kotest.extensions.spring.SpringTestContextCoroutineContextElement")
                }
                SpringContexts.current() shouldBe null
            }
        }

        given("테스트가 끝난 뒤") {
            then("고정 스펙의 설정이 다음 테스트로 새지 않는다") {
                RestDocsConfiguration.current.document.commonIncludeFiles shouldContainExactly emptyList()
            }
        }
    })
