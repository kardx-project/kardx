package io.github.kardx.annotation

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION

/**
 * AsciiDoc 문서 생성 시 추가 설명을 제공하는 어노테이션.
 *
 * 테스트 클래스에 붙이면 자동 생성 AsciiDoc 의 문서 제목(`= 사용자 API (사용자 관련 API 목록)`) 뒤에,
 * 테스트 메서드에 붙이면 그 섹션 제목 뒤에 붙는다.
 * 그래서 붙일 수 있는 곳도 클래스와 함수뿐이다. 테스트 클래스에 붙인 값은 Gradle 플러그인이 OpenAPI 최상위 `tags` 의 그 태그
 * (클래스 `@DisplayName`) 설명으로도 쓴다.
 *
 * ```kotlin
 * @AsciiDescription("사용자 관련 API 목록")
 * @DisplayName("사용자 API")
 * class UserControllerTest { ... }
 * ```
 *
 * @property value 설명 문자열
 * @property autoBracket true이면 [bracket]으로 자동 감싸기 (기본값: true)
 * @property bracket 감싸기에 사용할 괄호 종류 (기본값: ROUND)
 */
@Target(CLASS, FUNCTION)
@Retention(RUNTIME)
@MustBeDocumented
public annotation class AsciiDescription(
    val value: String,
    val autoBracket: Boolean = true,
    val bracket: Bracket = Bracket.ROUND,
) {
    /**
     * 설명 문자열을 감싸는 괄호 종류.
     *
     * @property opener 여는 괄호 문자
     * @property closer 닫는 괄호 문자
     */
    public enum class Bracket(private val opener: String, private val closer: String) {
        /** `[설명]`. */
        SQUARE("[", "]"),

        /** `{설명}`. AsciiDoc 은 `{이름}` 을 속성 참조로 바꾸므로 같은 이름의 속성이 있으면 치환된다. */
        CURLY("{", "}"),

        /** `(설명)`. 기본값. */
        ROUND("(", ")"),

        /** `<설명>`. */
        ANGLE("<", ">"),

        /** `<<설명>>`. AsciiDoc 에서 `<<...>>` 는 상호 참조(xref) 문법이라 링크로 렌더링된다. */
        DOUBLE_ANGLE("<<", ">>"),
        ;

        /** [value]를 괄호로 감싼 문자열을 반환한다. */
        public fun join(value: CharSequence): String = "$opener$value$closer"
    }
}
