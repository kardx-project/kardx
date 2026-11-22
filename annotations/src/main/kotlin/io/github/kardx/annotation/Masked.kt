package io.github.kardx.annotation

/**
 * 이 필드의 값이 마스킹되어 나간다는 사실을 문서에 표시한다 (예: 이름 `홍*동`, 전화번호 `010-****-1234`).
 *
 * **문서 표시용이며 값을 바꾸지 않는다.** 실제 마스킹은 앱(직렬화기 등)이 하고, 이 어노테이션은 그 사실을 문서에 적을 뿐이다.
 * 문서에 찍히는 요청·응답 헤더 값을 가리는 헤더 마스킹(`RestDocsSettings.maskedHeaders`)과는 다른 기능이다.
 *
 * 추출한 본문 필드(`responseFieldsOf<T>()`, `requestFieldsOf<T>()`, 메시지·SSE 페이로드)에 다음처럼 나타난다.
 * - AsciiDoc 필드 표: Description 칸 맨 앞에 `*Masked*: <value>` 줄 (`@Deprecated` 표시가 있으면 그 다음 줄)
 * - OpenAPI(Gradle 플러그인)·AsyncAPI 스키마 속성: `description` 끝에 `Masked: <value>`, 확장 속성 `x-masked: true`,
 *   [value] 가 있으면 `x-masked-rule`
 * - `describe()` 미리보기 표: 설명 앞에 `[Masked: <value>]`
 *
 * ```kotlin
 * data class UserResponse(
 *     @Masked("가운데 4자리를 *")
 *     val phone: String,
 * )
 * ```
 *
 * 회사에 이미 있는 마스킹 어노테이션도 같은 표시로 읽을 수 있다: yml `kardx.masked-annotations`
 * (코드로는 `RestDocsSettings.maskedAnnotations`). 직접 쓰는 필드 DSL 에서는 `masked()`·`masked "규칙"` 수식어를 쓴다.
 *
 * 붙이는 위치는 [Description] 과 같다: 생성자 프로퍼티(use-site target 생략), `@property:`, `@field:`, `@get:`,
 * Java 필드 · getter · record component.
 *
 * @property value 마스킹 규칙 설명 (예: `"가운데 글자를 *"`). 비우면 규칙 없이 `Masked` 만 표시한다
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Masked(val value: String = "")
