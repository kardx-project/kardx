package io.github.kardx.annotation

/**
 * Enum 의 코드 표를 Enum 코드 문서에 넣는다.
 *
 * `io.github.kardx.junit.EnumDocumentationExtension`(JUnit)이나 `io.github.kardx.kotest.EnumDocumentationExtension`
 * (Kotest)이 이 어노테이션이 붙은 Enum 을 찾아
 * 코드 문서(기본 `build/generated-asciidoc/code/enum-code.adoc`)에 [value] 를 제목으로 한 절과 표를 만든다.
 * 표는 상수마다 한 줄이고, 왼쪽 `Code` 열에 [codeProperty] 값, 오른쪽 `Description` 열에 [descriptionProperty] 값이 들어간다.
 *
 * ```kotlin
 * @EnumCode("상품 상태")
 * enum class ProductStatus(val description: String) {
 *     SALE("판매중"),
 *     SOLD_OUT("품절"),
 * }
 * ```
 *
 * ```asciidoc
 * [[상품_상태]]
 * == 상품 상태
 * enum : ProductStatus
 *
 * | Code | Description
 *
 * | SALE
 * | 판매중
 * ```
 *
 * 기본값 (속성을 비워 두면):
 * - [codeProperty]: JSON 에 실제로 나가는 값. `@JsonValue` 멤버 → 상수의 `@JsonProperty` → 상수 이름 순서다.
 * - [descriptionProperty]: 설정 `enumDescriptionProperty`(yml `kardx.enum-description-property`) 프로퍼티
 *   → `description` 프로퍼티 → 상수 이름 순서로, Enum 에 있는 첫 번째를 쓴다.
 *
 * 프로퍼티는 Enum 이 선언한 필드, getter(`getCode()`), 같은 이름의 인자 없는 메서드(`name()`) 순서로 찾는다.
 * 직접 적은 프로퍼티가 Enum 에 없으면 오타로 보고 Enum 과 속성 이름을 담은 예외로 문서 생성이 실패한다.
 * 프로퍼티 값이 `null` 인 상수는 상수 이름을 쓴다.
 *
 * `@Deprecated` 상수는 표에서 빠진다. 제목이 같은 Enum 이 둘이면 뒤의 것에 클래스 이름을 붙인다.
 *
 * @property value 절 제목. 앵커는 공백을 `_` 로 바꾼 값이다 (`상품_상태`)
 * @property codeProperty `Code` 열에 넣을 프로퍼티 이름. 비우면 JSON 값
 * @property descriptionProperty `Description` 열에 넣을 프로퍼티 이름. 비우면 위 기본값 순서
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class EnumCode(
    val value: String,
    val codeProperty: String = "",
    val descriptionProperty: String = "",
)
