[English](../kotest.md) | **한국어**

# Kotest 로 문서화하기

[문서 목록](README.md)

<!-- toc -->
**목차**

- [언제 쓰나](#언제-쓰나)
- [의존성](#의존성)
  - [Kotest 버전별 의존성](#kotest-버전별-의존성)
- [등록](#등록)
  - [스펙마다 등록](#스펙마다-등록)
  - [프로젝트 전체에 등록](#프로젝트-전체에-등록)
- [스펙 스타일별 예시](#스펙-스타일별-예시)
- [이름 규칙](#이름-규칙)
  - [문서 제목](#문서-제목)
  - [섹션 제목](#섹션-제목)
  - [스니펫 디렉터리 이름](#스니펫-디렉터리-이름)
  - [규칙 바꾸기](#규칙-바꾸기)
- [JUnit 확장과 같은 동작](#junit-확장과-같은-동작)
- [Spring 과 함께 쓰기](#spring-과-함께-쓰기)
  - [MockMvc](#mockmvc)
  - [WebTestClient](#webtestclient)
  - [WebSocket · STOMP 메시지](#websocket--stomp-메시지)
- [Enum 코드 문서](#enum-코드-문서)
- [index 와 OpenAPI](#index-와-openapi)
- [Kotest 5.x](#kotest-5x)
- [JUnit 테스트와 섞어 쓰기](#junit-테스트와-섞어-쓰기)
- [제한](#제한)
<!-- /toc -->

## 언제 쓰나

JUnit 확장(`AutoRestDocumentationExtension`)은 테스트 메서드와 파라미터 주입을 쓰므로 Kotest 스펙에는 붙일 수 없습니다. Kotest 의 `given`·`when`·`then`, `describe`·`it`, `context`·`test` 로 쓴 테스트를 문서화하려면 `kardx-kotest` 의 `RestDocsExtension` 을 씁니다.

DSL(`andDocument { }`), 필드 추출(`responseFields<T>()`), 설정(`RestDocsConfiguration`, yml), Gradle 플러그인은 JUnit 과 똑같습니다. 확장만 다르고, 스니펫 정리·실패 처리·설정 되돌리기·문서 쓰기는 JUnit 확장과 같은 코드를 씁니다.

> **Kotest 6.x 와 5.x 를 지원합니다.** 버전마다 아티팩트가 다르고(6.x `-kotest`, 5.x `-kotest5`) 패키지·공개 API 는 같습니다. 저장소는 6.2.7 과 5.9.1 로 검증합니다([Kotest 버전별 의존성](#kotest-버전별-의존성), [Kotest 5.x](#kotest-5x)).

## 의존성

### Kotest 버전별 의존성

Kotest 확장은 Kotest 버전마다 아티팩트가 다릅니다. 두 아티팩트는 같은 패키지(`io.github.kardx.kotest`)·같은 공개 API(`RestDocsExtension`, `SectionTitleProvider`, `SnippetNameProvider`, `TestPath`, `TestNames`, `EnumDocumentationExtension`)이므로 코드는 그대로 두고 의존성 좌표만 고릅니다.

**Gradle 플러그인을 쓰면 고르지 않아도 됩니다.** 테스트 의존성에 적힌 `io.kotest` 버전(BOM 포함)이 6.x 하나면 `-kotest`, 5.x 하나면 `-kotest5` 를 플러그인 버전으로 넣습니다. 버전을 적지 않았거나(다른 BOM 이 관리) 5·6 이 섞였으면 판정하지 않고 넣지 않으므로 그때는 직접 선언합니다. 자동 추가를 모두 끄려면 `kardx { addDependencies = false }` 입니다 ([자동으로 넣는 의존성](build.md#자동으로-넣는-의존성)).

| 프로젝트의 Kotest | 넣을 아티팩트 | Kotest Spring 확장 (Spring 테스트일 때) | 검증한 버전 |
|---|---|---|---|
| 6.x | `io.github.kardx-project:kardx-kotest` | `io.kotest:kotest-extensions-spring` (Kotest 와 같은 버전) | 6.2.7 |
| 5.x | `io.github.kardx-project:kardx-kotest5` | `io.kotest.extensions:kotest-extensions-spring:1.x` (좌표가 다름) | 5.9.1, Spring 확장 1.3.0 |

**두 아티팩트를 한 클래스패스에 함께 넣지 않습니다.** 같은 이름의 클래스가 두 벌이 되어 어느 쪽이 쓰일지 정해지지 않습니다. 두 모듈은 같은 Gradle capability(`io.github.kardx-project:kardx-kotest-extension`)를 내보내므로, Gradle 로 둘 다 들어오면(다른 의존성이 끌고 온 경우 포함) 의존성 해석이 capability 충돌로 실패하며 둘 중 하나를 고르라고 알려 줍니다. capability 는 Gradle 모듈 메타데이터로 전달되므로 Maven 처럼 POM 만 읽는 도구에서는 효과가 없습니다. 판이 맞지 않는 아티팩트를 넣으면(Kotest 6.x 에 `-kotest5`, 또는 반대) `RestDocsExtension` 을 만들 때 바로 실패합니다:

```
java.lang.IllegalStateException: kardx-kotest is built for Kotest 6.x, but Kotest 5.x is on the test classpath. Use kardx-kotest5 instead (same package and API), and keep only one of the two.
```

Kotest 6.x (Gradle 플러그인을 쓸 때):

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:6.2.7")   // 플러그인이 kardx-kotest 를 넣는다
    // Spring 테스트(@SpringBootTest)를 쓸 때. 생성자 주입과 yml 의 kardx.* 읽기
    testImplementation("io.kotest:kotest-extensions-spring:6.2.7")
}

tasks.test { useJUnitPlatform() }
```

Kotest 는 JUnit Platform 위에서 돌므로 `useJUnitPlatform()` 이 필요하고, Kotest Gradle 플러그인은 필요 없습니다. 파이프라인(index·HTML·OpenAPI)은 JUnit 과 같은 Gradle 플러그인 설정을 쓰며, index 는 따로 설정하지 않아도 문서를 만든 스펙이 모두 들어갑니다([index 와 OpenAPI](#index-와-openapi)).

Kotest 5.x (Gradle 플러그인을 쓸 때):

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")   // 플러그인이 kardx-kotest5 를 넣는다
    // Spring 테스트일 때. 5.x 의 Spring 확장은 io.kotest.extensions 그룹에 따로 배포된다
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.3.0")
}
```

등록 방법이 6.x 와 조금 다릅니다([Kotest 5.x](#kotest-5x)).

플러그인 없이 쓰거나 Kotest 버전을 적지 않은 프로젝트는 확장을 직접 넣습니다: `testImplementation("io.github.kardx-project:kardx-kotest:0.1")`(5.x 는 `-kotest5`, core 를 함께 가져온다). BOM 을 쓰면 버전 없이 씁니다. Kotest 와 Kotest Spring 확장의 버전은 프로젝트가 정합니다(이 모듈은 둘 다 `compileOnly`). Spring Boot 3.5·4.1 모두에서 검증했습니다.

## 등록

### 스펙마다 등록

스펙 본문에서 `extension(RestDocsExtension())` 으로 등록하고, 돌려받은 확장의 `provider` 를 MockMvc 에 넘깁니다. MockMvc 는 스펙 본문에서 한 번 만들어 두면 됩니다. 문서화 호출마다 그때 실행 중인 테스트의 스니펫 자리를 씁니다.

`responseFields<UserResponse>()` 같은 타입 인자 단축형은 `andDocument` 블록 타입의 멤버가 아니라 `io.github.kardx.extractor` 패키지의 확장 함수입니다. import 가 없으면 `responseFields<T>()` 가 컴파일되지 않으니(IDE 는 타입 인자 없는 멤버 `responseFields(...)` 만 찾습니다) 아래 import 를 함께 넣습니다. `pathMeans`·`type` 같은 항목 문법은 `io.github.kardx.dsl`, `andDocument`·`restDocsMockMvc` 는 `io.github.kardx.mockmvc` 에 있습니다.

```kotlin
import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.requestFields     // 단축형 requestFields<T>()
import io.github.kardx.extractor.responseFields    // 단축형 responseFields<T>()
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.web.context.WebApplicationContext

@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("사용자 API")                  // io.kotest.core.annotation.DisplayName (JUnit 것도 됨)
class UserApiDocsSpec(context: WebApplicationContext) : BehaviorSpec({
    val restDocs = extension(RestDocsExtension())
    val mockMvc = restDocsMockMvc(context, restDocs.provider)

    given("사용자가 있을 때") {
        `when`("단건 조회하면") {
            then("사용자 정보를 돌려준다") {
                mockMvc.perform(get("/api/v1/users/{id}", 1))
                    .andStatus(HttpStatus.OK)
                    .andDocument {
                        pathParameters("id" pathMeans "사용자 ID")
                        responseFields<UserResponse>()
                    }
            }
        }
    }
})
```

`build/generated-asciidoc/user-api-docs-spec.adoc` 이 생기고, 섹션 하나가 `사용자가 있을 때 - 단건 조회하면 - 사용자 정보를 돌려준다` 입니다.

### 프로젝트 전체에 등록

모든 스펙에 등록하려면 프로젝트 설정의 `extensions` 에 넣습니다(5.x 는 `override fun extensions()`, [Kotest 5.x](#kotest-5x)). 스펙에는 확장 인스턴스가 없으므로 `RestDocsExtension.currentTestProvider` 를 넘깁니다. 이 provider 는 지금 실행 중인 리프 테스트를 코루틴 문맥에서 찾습니다. 프로젝트 설정 클래스는 Kotest 6 이 기본으로 찾는 `io.kotest.provided.ProjectConfig`(테스트 소스의 `io/kotest/provided/ProjectConfig.kt`)에 두거나, 다른 이름이면 `src/test/resources/kotest.properties` 에 `kotest.framework.config.fqn=<클래스 이름>` 으로 알려 줍니다.

```kotlin
import io.github.kardx.kotest.RestDocsExtension
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension(), RestDocsExtension())
}

@SpringBootTest
class UserApiDocsSpec(context: WebApplicationContext) : FunSpec({
    val mockMvc = restDocsMockMvc(context, RestDocsExtension.currentTestProvider)

    test("사용자 조회") { ... }
})
```

문서화 호출이 없는 스펙은 스니펫도 문서도 만들지 않으므로 일반 단위 테스트와 함께 등록해도 됩니다. 스펙과 프로젝트 양쪽에 등록해도 테스트마다 한 번만 문서화합니다. 이때 그 스펙은 **스펙에 등록한 확장**이 문서화하고 스펙 문서(`.adoc`)도 그 확장이 한 번만 씁니다. 프로젝트 설정의 확장은 그 스펙을 건너뜁니다. 그래서 프로젝트 전체에 등록해 두고 스펙 하나만 `sectionTitle`·`snippetName`·`documentGenerator` 를 바꿀 수 있습니다(예: 그 스펙 문서만 `:toclevels:` 를 다르게).

```kotlin
// 프로젝트 설정에는 RestDocsExtension() 이 있다. 이 스펙 문서만 목차를 2단계로
class OrderApiDocsSpec(context: WebApplicationContext) : FunSpec({
    val restDocs = extension(
        RestDocsExtension(
            documentGenerator = {
                object : DocumentGenerator() {
                    override fun config(): Config = Config(tocLevels = 2)
                }
            },
        ),
    )
    val mockMvc = restDocsMockMvc(context, restDocs.provider)
    // ...
})
```

스펙에 등록한 것으로 보는 것은 스펙 본문의 `extension(...)` 처럼 스펙 인스턴스에 등록한 확장입니다. `@ApplyExtension(RestDocsExtension::class)` 와 Kotest 6 에서 deprecated 인 `override val extensions` 로 붙인 확장은 스펙에 등록한 것으로 알아보지 못합니다.

`currentTestProvider` 는 테스트 코루틴(과 그 자식 코루틴)에서만 지금 테스트를 찾습니다. 스펙에 등록한 확장의 `provider` 는 코루틴 밖의 스레드(테스트가 직접 만든 `Thread`·`ExecutorService`)에서 불려도 그 확장이 마지막으로 시작한 리프 테스트로 대신 찾지만, `currentTestProvider` 에는 이 대체가 없어 `IllegalStateException` 입니다. 새 스레드에서 문서화해야 하면 스펙에 등록한 확장의 `provider` 를 쓰거나 코루틴(`withContext(Dispatchers.IO)`)으로 옮깁니다.

## 스펙 스타일별 예시

리프 테스트(`then`, `it`, `test`, `should` 등)마다 섹션 하나입니다. 컨테이너(`given`, `when`, `describe`, `context`)는 섹션이 되지 않고 이름만 앞에 붙습니다. 스펙 스타일이 붙이는 `Given: `, `When: `, `Then: `, `Describe: `, `It: ` 같은 접두사와 포커스·제외 표시(`f:`, `!`)는 이름에 넣지 않습니다.

| 스타일 | 테스트 | 섹션 제목 | 스니펫 디렉터리 (`<spec>/` 아래) |
|---|---|---|---|
| `BehaviorSpec` | `given("사용자가 있을 때") { `when`("조회하면") { then("200") } }` | `사용자가 있을 때 - 조회하면 - 200` | `사용자가-있을-때-조회하면-200` |
| `FunSpec` | `test("사용자 조회")` | `사용자 조회` | `사용자-조회` |
| `FunSpec` | `context("목록") { test("첫 페이지") }` | `목록 - 첫 페이지` | `목록-첫-페이지` |
| `DescribeSpec` | `describe("GET /users/{id}") { it("returns user") }` | `GET /users/{id} - returns user` | `get-users-id-returns-user` |

섹션 순서는 테스트를 처음 실행한 순서(보통 선언 순서)입니다.

## 이름 규칙

### 문서 제목

스펙 클래스가 문서 하나입니다. 제목은 JUnit 테스트 클래스와 같은 규칙입니다.

- 스펙 클래스의 `@DisplayName` (Kotest `io.kotest.core.annotation.DisplayName` 또는 JUnit `org.junit.jupiter.api.DisplayName`), 없으면 클래스 단순 이름
- `@AsciiDescription` 이 있으면 제목 뒤에 붙고, OpenAPI 최상위 `tags` 의 설명이 됩니다
- 문서 파일 이름과 스니펫 디렉터리 첫 단계는 클래스 단순 이름의 kebab-case 입니다 (`UserApiDocsSpec` → `user-api-docs-spec.adoc`)

### 섹션 제목

바깥 컨테이너부터 리프까지의 테스트 이름을 ` - ` 로 잇습니다 (`TestNames.sectionTitle`). JUnit `@Nested` 테스트의 섹션 제목(`성공 - 조회`)과 같은 모양입니다. 이 값이 OpenAPI `summary` 와 AsyncAPI 메시지 요약, index 링크 글자가 됩니다.

### 스니펫 디렉터리 이름

스니펫 디렉터리 이름은 문서 식별자입니다. 섹션 앵커(`user-api-docs-spec.html#<이름>`), WireMock 매핑 파일 이름이 여기서 나옵니다. OpenAPI `operationId` 는 기본으로 컨트롤러 메서드 이름이고, `test-name` 규칙을 골랐거나 핸들러를 알 수 없을 때만 이 이름(`user-api-docs-spec.<이름>`)에서 나옵니다([index 와 OpenAPI](#index-와-openapi)). 그래서 순번이 아니라 테스트 이름으로 만듭니다. 테스트를 추가하거나 순서를 바꿔도 다른 테스트의 이름은 바뀌지 않습니다.

기본 규칙(`TestNames.snippetName`)은 이름마다 다음을 적용해 `-` 로 잇습니다.

1. 유니코드 NFC 로 정규화합니다 (macOS 처럼 자모가 풀린 한글을 합칩니다)
2. 소문자·숫자 뒤의 대문자 앞을 낱말 경계로 봅니다 (`getUser` → `get-user`, REST Docs 의 kebab-case 와 같은 결과)
3. 모두 소문자로 바꿉니다
4. 글자(한글 포함)·숫자·`_` 가 아닌 글자가 이어진 곳(공백, `/`, `.`, `:`, 괄호 …)을 `-` 하나로 바꾸고 앞뒤 `-` 를 뗍니다

| 테스트 이름 | 디렉터리 |
|---|---|
| `사용자 조회 (성공)` | `사용자-조회-성공` |
| `GET /api/v1.2/users` | `get-api-v1-2-users` |
| `getUser API 를 부르면` | `get-user-api-를-부르면` |

UTF-8 로 150 바이트를 넘으면 잘라 내고 전체 이름의 해시 8자리를 붙입니다(`…-3f2a9c1b`). 해시는 이름에서 만들므로 실행마다 같습니다.

JUnit 은 백틱 메서드 이름(`` fun `사용자 조회`() ``)을 REST Docs 규칙 그대로 디렉터리로 쓰고(공백 유지) 앵커만 다듬습니다. JVM 이 메서드 이름에서 `/`·`.`·`:` 같은 글자를 막아 주기 때문입니다. Kotest 이름에는 그런 제한이 없으므로 앵커 규칙(`DocumentGenerator.asciidocAnchor`)을 디렉터리에도 쓰고, 경로에서 문제가 되는 `.`·`:` 도 바꿉니다.

같은 스펙에서 두 테스트가 같은 이름이 되면 뒤의 테스트에 `-2`, `-3` 을 붙이고 경고합니다. 이 번호는 그 실행에서 테스트를 처음 시작한 순서를 따릅니다. 그래서 IDE 에서 뒤의 테스트 하나만 돌리면 번호 없는 이름(`…-a`)이 되어 전체 실행(`…-a-2`)과 스니펫 디렉터리·앵커(`test-name` 규칙이면 operationId 도)가 달라집니다. 이름을 바꿔 겹치지 않게 하세요.

```text
WARNING: kardx[KDX-403]: tests '목록 / 같은 이름 (a)' and '목록 / 같은 이름 [a]' in com.example.UserApiDocsSpec
map to the same snippet directory '목록-같은-이름-a'. Using '목록-같은-이름-a-2' for the second one; ...
```

### 규칙 바꾸기

생성자 인자 `sectionTitle`(`SectionTitleProvider`), `snippetName`(`SnippetNameProvider`), `documentGenerator`(`DocumentGeneratorFactory`)로 바꿉니다. 규칙은 `TestPath` 를 받습니다. `names` 는 테스트 이름 목록(바깥 컨테이너 → 리프, 스타일 접두사와 포커스·제외 표시 제외)이고 `specClass` 는 스펙 클래스입니다. 기본값은 `SectionTitleProvider.DEFAULT`, `SnippetNameProvider.DEFAULT` 입니다.

```kotlin
val restDocs = extension(
    RestDocsExtension(
        sectionTitle = { test -> test.names.last() },                         // 리프 이름만
        snippetName = { test -> test.names.drop(1).joinToString("-") { TestNames.slug(it) } },
        documentGenerator = { object : DocumentGenerator() { override fun config() = Config(tocLevels = 2) } },
    ),
)
```

`snippetName` 이 돌려준 값도 위 4단계를 `-` 사이마다 적용해 경로에 쓸 수 있게 다듬습니다(비면 `test`). `documentGenerator` 는 JUnit 확장의 생성자 인자 `documentGenerator` 와 같습니다([AsciiDoc 문서](asciidoc.md)).

생성자 인자에는 위처럼 그냥 람다를 넘깁니다. 규칙을 변수나 프로퍼티에 담을 때는 `val leafOnly = SectionTitleProvider { test -> test.names.last() }` 처럼 SAM 생성자를 씁니다.

## JUnit 확장과 같은 동작

| 동작 | Kotest |
|---|---|
| 지난 스니펫 지우기 | 리프 테스트가 이번 실행에서 그 디렉터리를 처음 쓸 때 |
| 실패한 테스트 | 스니펫을 지우고 문서·OpenAPI 에서 뺍니다 (테스트 안에서 문서화한 뒤 실패해도) |
| 스니펫이 없는 테스트 | 문서에 넣지 않습니다 (문서화하지 않는 검증 테스트, 건너뛴 테스트) |
| `configure` 되돌리기 | 리프 테스트 안과 그 `beforeEach`·`beforeTest`·`afterEach`·`afterTest` 에서(테스트 코루틴과 자식 코루틴에서) 한 `RestDocsConfiguration.configure` 는 테스트가 끝나면 그 블록만 뺍니다. 스펙 본문과 `beforeSpec` 에서 한 설정은 유지됩니다 (JUnit `@BeforeAll` 과 같음). 격리 모드는 아래 주의를 봅니다 |
| 문서 설정 | 사용자 `beforeEach`·`beforeTest` 가 모두 끝나고 테스트 본문이 시작하기 직전의 설정으로 문서를 씁니다(Kotest `BeforeInvocationListener`, JUnit 의 `@BeforeEach` 뒤와 같은 시점). 테스트 본문에서 한 `configure` 는 문서에 들어가지 않고, 마지막으로 실행한 리프 테스트의 것이 쓰입니다 |
| 문서 쓰기 | 스펙이 끝날 때. 격리 모드로 스펙 인스턴스가 여럿이면 인스턴스가 끝날 때마다 그때까지의 테스트로 다시 써서 마지막 문서에 모두 들어갑니다 |
| 문서 이름 겹침 | 다른 패키지의 같은 이름 스펙·테스트 클래스가 같은 문서를 쓰면 경고합니다 |

`IsolationMode.SingleInstance`(기본), `InstancePerRoot`, `InstancePerLeaf`, `InstancePerTest` 에서 문서 섹션·스니펫 디렉터리 이름이 같게 나오는 것을 확인했습니다.

**격리 모드에서는 스펙 본문·`beforeSpec` 대신 `beforeEach` 에서 설정합니다.** 격리 모드(`InstancePerRoot`·`InstancePerLeaf`·`InstancePerTest`)는 스펙 인스턴스를 여럿 만들고 인스턴스마다 스펙 본문과 `beforeSpec` 을 다시 실행합니다. 거기서 한 `configure` 는 테스트에 속하지 않아 되돌리지 않으므로 인스턴스 수만큼 쌓입니다(예: `document.commonIncludeFiles += "x.adoc"` 이면 문서에 `include::x.adoc[]` 가 여러 번). `beforeEach` 에서 한 설정은 테스트마다 더하고 끝날 때 빼므로 쌓이지 않습니다. 모든 스펙에 같은 값이면 프로젝트 설정(`beforeProject`)이나 yml 로 한 번만 정합니다.

```kotlin
class UserApiDocsSpec : FunSpec({
    isolationMode = IsolationMode.InstancePerRoot

    beforeEach { RestDocsConfiguration.configure { document.commonIncludeFiles = listOf("user.adoc") } }
    ...
})
```

## Spring 과 함께 쓰기

Kotest Spring 확장(6.x `io.kotest:kotest-extensions-spring`, 5.x `io.kotest.extensions:kotest-extensions-spring`)을 스펙에 적용하면(6.x `@ApplyExtension(SpringExtension::class)` 또는 프로젝트 설정, 5.x 는 [Kotest 5.x](#kotest-5x)) `RestDocsExtension` 이 리프 테스트마다 그 컨텍스트에서

- yml·`@TestPropertySource` 의 `kardx.*` 설정을 읽고
- 앱의 Jackson 매퍼 빈을 문서 예시 직렬화에 씁니다

Spring 확장이 클래스패스에 없거나 스펙에 없으면 코드 설정(`RestDocsConfiguration.configure`)만 씁니다. 클래스가 없어도 오류가 나지 않습니다.

코드 설정은 JUnit 의 `@BeforeEach` 처럼 `beforeEach` 에서 하면 테스트마다 적용되고 테스트가 끝나면 되돌아갑니다. 샘플의 [UserSearchDocsSpec](../../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/kotest/UserSearchDocsSpec.kt) 이 이렇게 씁니다.

```kotlin
beforeEach { DocsConfig.apply() }   // RestDocsConfiguration.configure { ... }
```

JUnit 문서화 테스트를 Kotest 로 옮기는 예는 Boot 4 샘플의 [kotest 패키지](../../samples/spring-boot-4/src/test/kotlin/com/example/restdocs/kotest)에 있습니다. JUnit 클래스마다 `FunSpec` 하나(`@Nested` → `context`, `@ParameterizedTest` → `withTests`, STOMP·WebSocket·SSE 포함)이고, 테스트 이름이 JUnit `@DisplayName` 과 같아 섹션 제목이 같습니다. 같은 API 를 JUnit 판이 이미 문서화하므로 이 스펙들은 `beforeEach` 의 `MirrorDocsConfig.apply()` 로 문서를 다른 디렉터리(`output.asciidocDirectory`)에 쓰고 OpenAPI·AsyncAPI·WireMock 조각을 만들지 않습니다. 확장은 JUnit 판과 같은 문서 생성기(목차 2단계)로 스펙에 등록하므로, 프로젝트 설정에 등록한 확장이 있어도 문서 머리가 JUnit 문서와 같습니다.

```kotlin
val restDocs = extension(MirrorDocsConfig.restDocsExtension())   // RestDocsExtension(documentGenerator = { SampleDocumentGenerator() })
val mockMvc = restDocsMockMvc(context, restDocs.provider)

beforeEach { MirrorDocsConfig.apply() }   // DocsConfig.apply() 뒤에 output.asciidocDirectory 변경, OpenAPI·AsyncAPI·WireMock 끔
```

### MockMvc

```kotlin
val mockMvc = restDocsMockMvc(context, restDocs.provider)
```

`MockMvcTester`, MockMvc Kotlin DSL(`mockMvc.get { }`) 진입점도 JUnit 과 같습니다([HTTP API 문서화](http-api.md)).

### WebTestClient

```kotlin
class ProductApiDocsSpec(context: ApplicationContext) : FunSpec({
    val restDocs = extension(RestDocsExtension())
    val client = restDocsWebTestClient(context, restDocs.provider)

    test("상품 조회") {
        client.get().uri("/api/v1/products/{id}", 1)
            .exchange()
            .expectStatus().isOk
            .andDocument { responseFields<ProductResponse>() }
    }
})
```

### WebSocket · STOMP 메시지

```kotlin
test("채팅 메시지 보내기") {
    MessageDocumentation(restDocs.provider).send("/app/chat.send", ChatSendRequest(roomId = 1, content = "안녕")) {
        description = "채팅방에 메시지를 보낸다."
        fields<ChatSendRequest>()
    }
}
```

AsyncAPI 메시지의 요약은 섹션 제목, 태그는 문서 제목입니다([SSE · WebSocket · AsyncAPI](messaging.md)).

## Enum 코드 문서

`EnumDocumentationExtension` 을 스펙이나 프로젝트 설정에 등록합니다. JUnit 의 같은 이름 확장과 같은 문서를 같은 코드로 만들고, 같은 패키지·출력 파일은 테스트 실행 전체에서 한 번만 씁니다. 형식을 바꾸려면 `format` 에 `EnumDocumentFormat` 객체를 넘깁니다([Enum 코드 문서 형식](customizing-snippets.md#enum-코드-문서-형식)).

```kotlin
class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(
        SpringExtension(),
        RestDocsExtension(),
        EnumDocumentationExtension(packageName = "com.myapp", title = "코드 정의"),
    )
}
```

## index 와 OpenAPI

Gradle 플러그인의 `apiDocsIndex` 는 설정 없이 문서를 만든 스펙과 JUnit 테스트 클래스를 모두 넣습니다. 스펙 문서를 쓸 때 남긴 기록을 읽으므로 클래스패스를 훑지 않고, 예전처럼 `baseClass = "io.kotest.core.spec.Spec"` 을 적을 필요가 없습니다. 항목 순서는 `@DisplayName` 순서입니다.

`index.packageName`·`index.baseClass` 를 함께 정하면 그 패키지에서 `baseClass` 를 상속한 테스트 클래스와 **같은 패키지의 Kotest 스펙**만 넣습니다(Kotest 스펙은 JUnit 기본 클래스를 상속할 수 없기 때문). 이때도 문서를 만든 스펙만 항목이 됩니다.

OpenAPI 의 태그는 스펙의 `@DisplayName`, 없으면 클래스 단순 이름(문서 제목과 같음, JUnit 과 같은 규칙)이고, `summary` 는 섹션 제목입니다 ([OpenAPI 태그와 요약](http-api.md#openapi-태그와-요약)). `operationId` 는 JUnit 과 같은 규칙으로 정합니다([operationId](http-api.md#operationid)): 기본은 요청을 처리한 컨트롤러 메서드 이름(`getUser`)이라 테스트 이름이 한글이어도 영문 id 가 됩니다. 테스트 하나의 id 는 `openApi { operationId = "..." }` 로 직접 정합니다.

```kotlin
then("그 사용자 목록을 돌려준다") {
    mockMvc.perform(get("/api/v1/users/search").param("nickname", "홍길동"))
        .andDocument {
            responseFields<List<UserResponse>>()
            openApi { operationId = "searchUsersByNickname" }
        }
}
```

`test-name` 규칙(`kardx.open-api.operation-id-rule: test-name`)을 골랐거나 핸들러를 알 수 없는 요청(함수형 라우터, 서버 쪽을 볼 수 없는 WebTestClient 바인딩)은 `<spec>.<스니펫 디렉터리 이름>` 이 되고 한글이 그대로 남습니다. 한글 operationId 는 OpenAPI 스펙으로는 유효하고 Swagger UI 도 잘 보여 주지만, 클라이언트 코드 생성기(OpenAPI Generator 등)는 메서드 이름을 만들 때 ASCII 가 아닌 글자를 지우는 경우가 많습니다. 그러면 이름이 비거나(`user-api-docs-spec.사용자-조회` → `userApiDocsSpec`) 여러 operation 이 같은 이름이 되어 생성이 실패하거나 번호가 붙습니다. 해결: (a) 그 테스트에 `openApi { operationId = "..." }` 를 쓰거나, (b) 규칙을 `handler-method`(기본)나 `path`(`GET /users/{id}` → `getUsersById`, 핸들러를 몰라도 영문)로 바꿉니다.

## Kotest 5.x

`kardx-kotest5` 는 Kotest 5.x API 로 컴파일한 같은 확장입니다. 이름 규칙, 실패한 테스트 제외, 설정 되돌리기와 기억 시점(`beforeEach` 뒤, 본문 전), 문서·OpenAPI 결과는 6.x 와 같습니다. 6.x 판과 같은 테스트(BehaviorSpec·FunSpec·DescribeSpec, 한글 이름, 격리 모드, 프로젝트 등록, Spring 확장 없이 동작 등)를 Kotest 5.9.1 엔진으로 돌려 확인합니다.

**등록.** 스펙 본문의 `extension(RestDocsExtension())` 은 6.x 와 같습니다. 그 밖의 등록은 5.x API 를 씁니다.

```kotlin
// 프로젝트 설정: 5.x 는 extensions 가 함수다 (6.x 는 val extensions)
class ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, RestDocsExtension())
}

// 스펙 클래스에서 함수로 등록할 때는 인스턴스를 프로퍼티에 두고 같은 것을 돌려준다
@SpringBootTest
class UserApiDocsSpec(context: WebApplicationContext) : FunSpec({
    val mockMvc = restDocsMockMvc(context, RestDocsExtension.currentTestProvider)

    test("사용자 조회") { ... }
}) {
    private val restDocs = RestDocsExtension()

    override fun extensions(): List<Extension> = listOf(restDocs)
}
```

5.x 는 `extensions()` 를 부를 때마다 목록을 다시 받습니다. `override fun extensions() = listOf(RestDocsExtension())` 처럼 매번 새로 만들면 테스트를 문서화한 인스턴스와 스펙이 끝났을 때 불리는 인스턴스가 달라 스니펫은 생기지만 스펙 문서(`.adoc`)가 쓰이지 않습니다.

**Spring 확장.** 5.x 의 Spring 확장(`io.kotest.extensions:kotest-extensions-spring:1.3.0`)에서 `SpringExtension` 은 클래스가 아니라 값이므로 `@ApplyExtension(SpringExtension::class)` 대신 프로젝트 설정이나 스펙의 `extensions()` 에 넣습니다. 생성자 주입(`class Spec(context: WebApplicationContext)`)에는 `SpringAutowireConstructorExtension` 도 넣습니다. 이렇게 적용하면 yml 의 `kardx.*` 과 앱의 Jackson 매퍼를 6.x 와 똑같이 읽습니다. 프로젝트 설정 클래스는 6.x 와 같이 `io.kotest.provided.ProjectConfig` 에 두면 찾습니다.

```kotlin
class ProjectConfig : AbstractProjectConfig() {
    override fun extensions() = listOf(SpringExtension, SpringAutowireConstructorExtension)
}
```

**6.x 와 다른 점.**

| 항목 | 6.x | 5.x |
|---|---|---|
| 격리 모드 | `InstancePerRoot`·`InstancePerLeaf`·`InstancePerTest` | `InstancePerLeaf`·`InstancePerTest` (5.x 에는 `InstancePerRoot` 가 없다) |
| 데이터 테스트(`withData`) | 행이 리프(`TestType.Test`)·컨테이너로 나뉜다 | 행이 `TestType.Dynamic` 이라 리프인지 미리 알 수 없어 리프로 본다(5.x Spring 확장과 같음). `withData` 를 중첩하면 안쪽 행은 바깥 행의 섹션 하나에 들어간다 |
| 프로젝트 설정 등록 | `override val extensions` | `override fun extensions()` |
| 스펙 클래스 등록 | `@ApplyExtension`, `override val extensions` | `override fun extensions()` (인스턴스를 프로퍼티에) |
| Spring 확장 | `io.kotest:kotest-extensions-spring:6.x`, `@ApplyExtension(SpringExtension::class)` | `io.kotest.extensions:kotest-extensions-spring:1.x`, `extensions()` 에 `SpringExtension` 값 |
| 공개 API | — | 같다. 5.x 의 리스너 인터페이스가 `name` 속성을 가져서 바이너리 API 덤프에 `getName()` 이 더 보인다 |

## JUnit 테스트와 섞어 쓰기

한 프로젝트에 JUnit 테스트와 Kotest 스펙을 함께 둘 수 있습니다. 둘 다 JUnit Platform 에서 돌므로 `test` 태스크 하나로 실행되고, 문서는 같은 `build/generated-asciidoc` 에 나란히 생깁니다. index·HTML·OpenAPI·AsyncAPI 에도 함께 들어갑니다. 샘플(`samples/spring-boot-3`, `samples/spring-boot-4`)이 JUnit 테스트 여럿과 MockMvc Kotest 스펙(프로젝트 전역 등록 포함)을, `samples/spring-boot-4-webflux` 가 WebTestClient Kotest 스펙(`ProductSummaryDocsSpec`)을 함께 씁니다.

- `testImplementation` 에 버전을 적은 `kotest-runner-junit5` 를 넣으면 Gradle 플러그인이 core 와 그 판의 Kotest 모듈(`-kotest`·`-kotest5`)을 넣습니다 (플러그인 없이 쓰거나 버전을 적지 않았으면 직접)
- 설정은 JVM 하나에서 공유합니다. 어느 쪽이든 테스트 안에서 한 `configure` 는 테스트가 끝나면 되돌아갑니다
- 같은 단순 이름의 JUnit 클래스와 Kotest 스펙은 문서 이름이 겹치므로 경고합니다
- 같은 API 를 JUnit 테스트와 Kotest 스펙 양쪽에서 문서화하면 OpenAPI 에서는 경로·메서드가 같은 operation 하나로 합쳐지고(operationId 그대로) 응답 예시만 하나 더 붙습니다. AsyncAPI 는 같은 메시지에 예시를 더하고(같은 내용이면 하나), index 에는 문서가 둘 생깁니다. 한쪽만 실으려면 그쪽 `beforeEach` 에서 `output.asciidocDirectory` 를 다른 디렉터리로, `openApi.enabled`·`asyncApi.enabled`·`output.wireMockStubs` 를 `false` 로 둡니다(Boot 4 샘플의 `MirrorDocsConfig`). 서버 상태(순번 등)를 바꾸는 테스트는 같은 Spring 컨텍스트를 이어 쓰므로 뒤에 도는 쪽의 예시 값이 달라질 수 있습니다(`@DirtiesContext`)

## 제한

- **Kotest 버전에 맞는 아티팩트를 하나만 넣습니다.** 5.x 와 6.x 는 테스트 이름 속성(5.x `testName`, 6.x `name`)과 테스트 결과 클래스의 패키지(5.x `io.kotest.core.test.TestResult`, 6.x `io.kotest.engine.test.TestResult`)가 달라 한 바이너리로 둘 다 지원할 수 없습니다. 맞지 않는 아티팩트는 `RestDocsExtension` 을 만들 때 `IllegalStateException` 으로 바로 실패합니다([Kotest 버전별 의존성](#kotest-버전별-의존성)). Kotest 4.x 이하는 지원하지 않습니다
- **한 스펙 안의 테스트를 병렬로 실행하는 것은 권하지 않습니다**(`concurrency`, `testExecutionMode`). 각 테스트는 자기가 더한 `configure` 만 빼므로 서로의 설정을 지우지는 않지만, 설정 값이 JVM 전역 하나라 동시에 도는 테스트의 설정이 섞이고, 문서에 쓰는 "마지막 테스트" 와 섹션 순서가 실행마다 달라집니다. provider 는 테스트마다 코루틴 문맥으로 구분됩니다
- 스펙끼리 병렬로 돌릴 때의 제한(동시에 서로 다른 `configure`·yml 값으로 문서화하면 섞임)은 JUnit 과 같습니다([병렬 실행](configuration.md#병렬-실행))
- 리프 테스트 밖(스펙 본문, 컨테이너 블록)에서 문서화하면 `IllegalStateException: kardx[KDX-401]: no Kotest leaf test is running` 입니다. 요청과 `andDocument` 는 `then`·`it`·`test` 안에서 합니다
- 테스트 안에서 새 스레드를 직접 만들어(`Thread`, `ExecutorService`) 문서화하면 `currentTestProvider` 는 지금 테스트를 찾지 못하고, 스펙의 `provider` 는 마지막으로 시작한 테스트로 대신 찾습니다([프로젝트 전체에 등록](#프로젝트-전체에-등록)). 그런 스레드에서 한 `configure` 도 테스트에 속하지 않아 되돌리지 않습니다. 코루틴(`withContext(Dispatchers.IO)`, `launch`)은 문맥을 이어받으므로 됩니다
