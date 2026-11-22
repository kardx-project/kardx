package io.github.kardx.config

/**
 * 문서 필드 이름 변환 규칙 (예: camelCase → snake_case). [RestDocsSettings] 의 `extraction.propertyNameStrategy` 로 정한다.
 * `@JsonProperty` 로 명시한 이름과 클래스의 `@JsonNaming` 이 이 규칙보다 우선한다.
 *
 * ```kotlin
 * extraction {
 *     propertyNameStrategy = PropertyNameStrategy { it.name.replace(Regex("([A-Z])"), "_$1").lowercase() }
 * }
 * ```
 */
public fun interface PropertyNameStrategy {
    /** [input] 의 이름을 문서에 쓸 이름으로 바꾼다. */
    public fun translate(input: PropertyNameInput): String
}

/**
 * [PropertyNameStrategy] 가 받는 입력. 라이브러리가 만들어 넘기며, 앞으로 정보가 더해질 수 있다.
 *
 * @property name 소스에 선언한 프로퍼티 이름 (`createdAt`)
 */
public class PropertyNameInput internal constructor(public val name: String) {
    override fun toString(): String = "PropertyNameInput($name)"
}
