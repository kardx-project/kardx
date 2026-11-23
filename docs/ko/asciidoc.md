[English](../asciidoc.md) | **한국어**

# AsciiDoc 문서

[문서 목록](README.md)

<!-- toc -->
**목차**

- [브라우저에서 보기](#브라우저에서-보기)
- [문서 자동 생성](#문서-자동-생성)
  - [섹션 순서](#섹션-순서)
  - [테스트 모양별 스니펫·섹션](#테스트-모양별-스니펫섹션)
  - [문서 모양 바꾸기](#문서-모양-바꾸기)
- [index 문서](#index-문서)
- [Enum 코드 문서](#enum-코드-문서)
<!-- /toc -->

테스트가 끝나면 스니펫을 모아 AsciiDoc 문서를 만듭니다. 여러 문서를 묶는 index 와 Enum 코드 표도 만들 수 있습니다.

## 브라우저에서 보기

AsciiDoc(`.adoc`)은 `asciidoctor` 태스크가 HTML 로 바꾼 뒤에 봅니다. `.adoc` 은 스니펫을 `{snippets}` 경로로 include 하므로, IDE 미리보기에서는 스니펫이 비어 보일 수 있습니다.

| 보고 싶은 것 | 앱 주소 | 파일 |
|---|---|---|
| index (모든 문서 목록과 섹션 링크) | `http://localhost:8080/docs/index.html` | `build/api-docs/index.html` |
| 테스트 클래스 하나의 문서 | `http://localhost:8080/docs/user-api-docs-test.html` | `build/api-docs/user-api-docs-test.html` |
| 문서 안의 섹션 | `.../user-api-docs-test.html#get-user` | 같은 파일 + `#get-user` |
| Enum 코드 문서 | `http://localhost:8080/docs/code/enum-code.html` | `build/api-docs/code/enum-code.html` |
| Enum 표 하나 | `.../code/enum-code.html#사용자_상태` | 같은 파일 + `#사용자_상태` |
| 변환 전 AsciiDoc | 없음 | `build/generated-asciidoc/*.adoc` |

주소의 `/docs` 는 Gradle 플러그인의 `bootJar.path`(기본 `static/docs`)에서, 포트는 앱 설정에서 옵니다. 파일 경로는 `./gradlew apiDocs` 기준이고 플러그인의 `outputDirectory` 로 바꿀 수 있습니다. 문서 이름은 테스트 클래스 이름, `#` 뒤는 테스트 메서드 이름을 kebab-case 로 바꾼 값입니다 (`ProductApiDocsTest.getProductNotFound` → `product-api-docs-test.html#get-product-not-found`).

- 왼쪽 목차(`:toc: left`)의 항목을 누르면 해당 섹션으로 이동합니다. index 의 섹션 링크도 같은 앵커를 씁니다.
- Enum 표 앵커는 `@EnumCode` 제목의 공백을 `_` 로 바꾼 값입니다.

## 문서 자동 생성

`AutoRestDocumentationExtension` 은 REST Docs 의 `RestDocumentationExtension` 을 대신합니다. Kotest 스펙은 같은 동작을 하는 `RestDocsExtension` 을 씁니다([Kotest](kotest.md)). 아래 동작과 문서 모양은 둘이 같고, 섹션을 고르는 방법(테스트 메서드 대신 실행된 리프 테스트)과 이름 규칙만 다릅니다. 테스트 메서드에 `RestDocumentationContextProvider` 를 넣어 주는 것은 같고, 다음을 더 합니다.

1. 테스트 메서드를 실행하기 전에 그 메서드의 지난 스니펫을 지웁니다. 파라미터를 지운 뒤 테스트 하나만 다시 돌려도 옛 스니펫이 남지 않습니다.
2. 테스트 클래스가 끝나면 스니펫을 모아 `<class-name>.adoc` 을 씁니다. 클래스 `@DisplayName` 이 제목, 메서드 `@DisplayName` 이 섹션 제목입니다. 상위 클래스에 선언한 테스트, `@ParameterizedTest`·`@RepeatedTest` 도 넣습니다. 테스트 모양별 스니펫 디렉터리와 섹션은 [테스트 모양별 스니펫·섹션](#테스트-모양별-스니펫섹션)에 있습니다.
3. 스니펫을 만들지 않은 테스트(실패 케이스 검증 등)는 섹션을 만들지 않습니다. **실패한 테스트**는 스니펫을 지우고 문서에서 빼므로, 실패한 API 가 문서·OpenAPI 에 남지 않습니다.
   - 섹션 앵커는 메서드 이름을 kebab-case 로 바꾼 값입니다. 백틱 메서드 이름(`` fun `사용자 조회 성공`() ``)은 공백을 `-` 로 바꿔 `#사용자-조회-성공` 이 됩니다 (스니펫 디렉터리는 REST Docs 규칙 그대로).
   - 패키지만 다른 같은 이름의 테스트 클래스는 같은 문서·스니펫 디렉터리를 쓰므로 경고가 나옵니다. 이름을 다르게 하세요.
4. 문서 처음과 끝, 그리고 스크롤해도 보이는 왼쪽 목차의 제목 자리(`Table of Contents` 위)에 index 로 돌아가는 링크(`← 메인으로 돌아가기`. 기본 언어 `en` 이면 `← Back to index`, [문서 언어](configuration.md#문서-언어))를 넣습니다. 대상·글자는 `index-link`, `index-link-text` 설정으로 바꾸고, `index-link: none` 이면 셋 다 넣지 않습니다.
   - 목차 링크는 문서 헤더의 `:toc-title: pass:[...]` 한 줄입니다. 목차를 쓰지 않는 설정(`Config(toc = "")`)에서는 넣지 않습니다.
   - 목차 링크만 빼려면 `DocumentGenerator.tocTitleAttribute()` 를 재정의해 빈 문자열을 돌려줍니다 ([문서 틀 바꾸기](customizing-snippets.md#문서-틀-바꾸기)).
   - Enum 코드 문서(`code/enum-code.html`)는 목차 링크만 넣고, 대상은 한 단계 위(`../index.html`)로 맞춥니다. index 문서 자신에는 넣지 않습니다.

문서를 만들 때는 테스트가 실행될 때(`@BeforeEach` 이후)의 설정을 씁니다. 그래서 테스트가 끝나 `@BeforeEach` 의 설정이 [되돌아가도](configuration.md#바꾸는-방법) `document.commonIncludeFiles` 같은 문서 설정이 유지됩니다.

```kotlin
@AsciiDescription("회원 관리")                                        // 제목 옆 "(회원 관리)"
@DisplayName("사용자 API")
class UserApiDocsTest : ApiDocsTest() {
    @Test
    @AsciiDescription("관리자 전용", bracket = AsciiDescription.Bracket.SQUARE)   // "[관리자 전용]"
    @DisplayName("사용자 삭제")
    fun deleteUser() { ... }
}
```

### 섹션 순서

섹션은 기본으로 메서드 이름순, `@Nested` 클래스는 클래스 이름순입니다. JUnit 의 순서 어노테이션이 있으면 그것을 따릅니다.

| 테스트 클래스에 붙인 것 | 섹션 순서 |
|---|---|
| `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` + 메서드 `@Order(n)` | `@Order` 값 순서. `@Order` 가 없는 메서드는 뒤에 온다 |
| `@TestMethodOrder(MethodOrderer.DisplayName::class)` | `@DisplayName` 순서 |
| `@TestMethodOrder(MethodOrderer.MethodName::class)` | 메서드 이름순 |
| `@TestClassOrder(ClassOrderer.OrderAnnotation::class)` + `@Nested` 클래스 `@Order(n)` | `@Nested` 클래스를 `@Order` 순서로 (`ClassName`·`DisplayName` 도 같다) |
| 없음, 또는 `Random`·직접 만든 orderer | 아래 `document-section-order` 설정 |

```kotlin
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@DisplayName("상품 API")
class ProductApiDocsTest : ApiDocsTest() {
    @Test @Order(1) @DisplayName("상품 조회") fun getProduct() { ... }
    @Test @Order(2) @DisplayName("상품 검색") fun searchProducts() { ... }
    @Test @Order(3) @DisplayName("상품 등록") fun createProduct() { ... }
}
```

순서 어노테이션이 없을 때의 기본값은 설정으로 고릅니다. 바깥 클래스에 붙인 `@TestMethodOrder` 는 `@Nested` 클래스에도 적용합니다.

| 설정 | yml | 값 |
|---|---|---|
| `document.sectionOrder` | `document-section-order` | `METHOD_NAME`(`method-name`, 기본값), `DISPLAY_NAME`(`display-name`: `@DisplayName` 순) |

소스에 쓴 순서(선언 순서)는 고를 수 없습니다. JVM 리플렉션은 메서드를 선언 순서로 돌려준다는 보장이 없어 실행 환경마다 결과가 달라질 수 있기 때문입니다 (JUnit 의 기본 순서도 같은 이유로 "정해져 있지만 드러나지 않는" 순서입니다). 선언 순서대로 두려면 `@Order` 를 붙입니다.

### 테스트 모양별 스니펫·섹션

스니펫 디렉터리는 `<문서 클래스>/<메서드 자리>` 입니다 (REST Docs 의 `{class-name}/{method-name}`, kebab-case). 테스트 모양마다 다음처럼 정해 서로 덮어쓰지 않습니다.

| 테스트 | 스니펫 디렉터리 | 문서 섹션 |
|---|---|---|
| `UserApiTest.getUser()` | `user-api-test/get-user` | `user-api-test.adoc` 의 `#get-user` |
| `UserApiTest` 안 `@Nested inner class Success` 의 `getUser()` | `user-api-test/success-get-user` | 바깥 클래스 문서 `user-api-test.adoc` 의 `#success-get-user`. 제목은 `성공 - 사용자 조회` 처럼 `@Nested` 클래스 표시 이름이 앞에 붙는다. `@Nested` 클래스는 문서를 따로 만들지 않는다 |
| `@ParameterizedTest getUser(id)` 의 2번째 실행 | `user-api-test/get-user/2` | 실행마다 섹션 `#get-user-2`. 제목 뒤에 실행 표시 이름(`[2] id=2`, `@ParameterizedTest(name = ...)`)이 붙는다 |
| `@RepeatedTest` | `user-api-test/get-user` (실행마다 같은 곳) | 섹션 하나. 같은 요청을 반복하므로 마지막 실행이 남는다 |
| 이름이 같은 오버로드 `create()`, `create(info: TestInfo)` | `create`, `create-test-info` (인자 있는 쪽에 인자 타입 이름) | 섹션 둘 |

- OpenAPI 에서는 경로·메서드가 같은 실행들이 operation 하나로 합쳐집니다(상태 코드별 응답).
- 파라미터 테스트의 실패한 실행은 그 실행의 디렉터리만 지워 문서에서 빠집니다.
- `@DisplayName` 은 AsciiDoc 제목에 그대로 들어가므로 인라인 서식으로 해석됩니다 (`*강조*`, `` `코드` ``). 글자 그대로 보이려면 `\*` 처럼 백슬래시로 막거나 `+*별*+` 로 감쌉니다. OpenAPI 의 summary·tag 에는 그대로 나갑니다.

### 문서 모양 바꾸기

넣을 스니펫 종류(`document-snippets`), 소제목·섹션 제목·목차 위치(`DocumentGenerator` 재정의), 스니펫 표의 열(템플릿 재정의), 직접 만든 스니펫은 [스니펫과 문서 모양 바꾸기](customizing-snippets.md)에 모았습니다.

## index 문서

여러 테스트 클래스의 문서를 하나의 index(`/docs/index.html`)로 묶습니다. Gradle 플러그인의 `apiDocsIndex` 태스크가 만들고, 설정은 모두 선택입니다.

- **목록**: 기본은 문서를 만든 모든 테스트 클래스(JUnit·Kotest)입니다. 테스트가 클래스별 문서를 쓸 때 `build/generated-asciidoc/.restdocs-documents/<문서>.properties` 에 문서 이름·제목·순서를 함께 남기고 index 는 그것을 읽으므로 클래스패스를 훑지 않습니다. 클래스마다 파일이 따로라 병렬 fork 에서도 섞이지 않고, 다시 돌지 않은 클래스(증분 실행)의 기록도 남습니다.
- **템플릿**: `src/docs/asciidoc/index-template.adoc` 이 있으면 그 파일의 마커 줄(`// restdocs:api-index`) 자리에 테스트 클래스별 항목과 섹션 링크를 넣습니다. 없으면 생성 문서와 같은 헤더 속성을 쓰는 내장 템플릿(`= <index.title>` + `== API 목록`. 기본 언어 `en` 이면 `== API list`)을 씁니다. 제목 기본값은 프로젝트 이름입니다.

```asciidoc
= 우리 서비스 API
:toc: left

== API 목록

// restdocs:api-index
```

```kotlin
kardx {
    index {
        title = "우리 서비스 API"                       // 내장 템플릿의 제목. 기본값 프로젝트 이름
        // 아래 둘은 함께 정한다. 정하면 그 패키지에서 기본 클래스를 상속한 테스트(와 같은 패키지의 Kotest 스펙)만 넣는다
        packageName = "com.myapp"                       // 하위 패키지까지 찾는다
        baseClass = "com.myapp.support.ApiDocsTest"     // 이 클래스를 상속한 구체 클래스
    }
}
```

`packageName`·`baseClass` 중 하나만 정했거나, 정하지 않았는데 문서를 쓴 테스트가 없으면 `apiDocsIndex` 는 이유를 한 줄 남기고 건너뜁니다. `baseClass` 를 정하면 그 하위 클래스에 더해 같은 패키지의 Kotest 스펙도 찾습니다(문서를 만든 스펙만 항목이 됨, [Kotest](kotest.md#index-와-openapi)).

플러그인 없이 쓰려면 `DocumentationIndexCli` 를 `--output` 인자로 실행합니다. 나머지 인자는 다음과 같습니다.

- `--documents <디렉터리>`: 생성된 테스트 문서(`*.adoc`)와 기록이 있는 디렉터리(주지 않으면 `--output` 파일의 디렉터리). 플러그인은 index 를 `build/kardx/index/` 에 만들고 `--documents` 로 `asciidocDirectory` 를 넘깁니다.
- `--template`: 템플릿(없으면 내장 템플릿, 제목은 `--title`).
- `--package`·`--base-class`: 패키지를 훑을 때 함께 줍니다.

코드에서는 `DocumentationIndex.write(template, output, testClasses)` 를 부르고, 문서 디렉터리가 다르면 `DocumentationIndex.write(template, output, testClasses, documents)` 를 씁니다. 링크는 `./<문서>.html` 이므로 렌더링할 때는 index 와 테스트 문서를 같은 디렉터리에 모읍니다.

항목은 `@DisplayName`(없으면 클래스 이름) 순서이고, 마커가 없으면 끝에 붙입니다. 순서를 정하려면 테스트 클래스에 JUnit `@Order(n)` 을 붙입니다. `@Order` 가 있는 클래스가 값 순서로 앞에 오고, 나머지는 `@DisplayName` 순서로 뒤에 옵니다.

```kotlin
@Order(1)
@DisplayName("인증 API")
class AuthApiDocsTest : ApiDocsTest()
```

## Enum 코드 문서

`@EnumCode` 를 붙인 Enum 을 스캔해 코드 표 문서를 만듭니다. Enum 마다 제목(`value`)으로 절 하나와 `Code` / `Description` 표가 생기고, 상수마다 한 줄입니다.

```kotlin
@EnumCode("사용자 상태")
enum class UserStatus(@get:JsonValue val code: String, val description: String) {
    ACTIVE("A", "활성"),
    INACTIVE("I", "비활성"),

    @Deprecated("INACTIVE 를 쓴다")
    DORMANT("D", "휴면"),
}

class MyEnumDocs : EnumDocumentationExtension(packageName = "com.myapp", title = "코드 정의")

@ExtendWith(MyEnumDocs::class, AutoRestDocumentationExtension::class)
abstract class ApiDocsTest
```

Kotest 는 `io.github.kardx.kotest.EnumDocumentationExtension(packageName = "com.myapp", title = "코드 정의")` 를 스펙이나 프로젝트 설정에 등록합니다([Kotest](kotest.md#enum-코드-문서)).

결과 (`build/generated-asciidoc/code/enum-code.adoc`):

```asciidoc
[[사용자_상태]]
== 사용자 상태
enum : UserStatus

[cols="3,7"]
|===
| Code | Description

| A
| 활성

| I
| 비활성

|===
```

| 속성 | 표의 열 | 비워 두면 (기본값) |
|---|---|---|
| `value` | 절 제목·앵커 | (필수) |
| `codeProperty` | `Code` | JSON 에 실제로 나가는 값: `@JsonValue` 멤버 → 상수의 `@JsonProperty` → 상수 이름. 필드 설명 뒤의 값 목록·`enumOf` 와 같은 값 |
| `descriptionProperty` | `Description` | 설정 `extraction.enumDescriptionProperty`(yml `kardx.enum-description-property`, 기본 `description`) 프로퍼티 → `description` 프로퍼티 → 상수 이름 |

- 직접 지정: `@EnumCode("결제 상태", codeProperty = "code", descriptionProperty = "label")`. 상수 이름을 코드로 쓰려면 `codeProperty = "name"`.
- 프로퍼티는 Enum 이 선언한 필드 → getter(`getCode()`) → 같은 이름의 인자 없는 메서드(`name()`) 순서로 찾습니다. 값이 `null` 인 상수는 상수 이름을 씁니다.
- 직접 적은 프로퍼티가 Enum 에 없으면 오타로 보고 문서 생성이 실패합니다: `@EnumCode(descriptionProperty = "descripton") on com.myapp.UserStatus: the enum has no property 'descripton'. ...`. 기본값을 찾을 때만 다음 후보로 조용히 넘어갑니다.
- 출력은 `build/generated-asciidoc/code/enum-code.adoc` 입니다. `fileName`, `outputDirectory` 로 바꿀 수 있습니다.
- 하위 패키지와 여러 jar 를 스캔합니다. `@Deprecated` 상수는 뺍니다.
- 테스트 클래스가 여러 개여도 실행 한 번에 한 번만 만듭니다.
- 다른 문서에서는 `link:./code/enum-code.html#사용자_상태[사용자 상태]` 로 링크합니다. 앵커는 제목의 공백을 `_` 로 바꾼 값입니다.
- 머리글이나 열 비율은 `AsciiDocEnumDocumentFormat` 을 상속해 바꿉니다 ([Enum 코드 문서 형식](customizing-snippets.md#enum-코드-문서-형식)).
