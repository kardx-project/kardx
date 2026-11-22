package io.github.kardx.tooling.internal.sourcedocs;

/**
 * 속성 설명의 출처. 뒤에 있을수록 우선한다. Kotlin: 속성 자신의 KDoc > 클래스 KDoc {@code @property} > 클래스·생성자
 * KDoc {@code @param}. Java: 필드 Javadoc > getter Javadoc > record Javadoc {@code @param}.
 */
enum DocPrecedence {
    /** 클래스·생성자·record 문서의 {@code @param}. */
    PARAM_TAG,
    /** 클래스 KDoc 의 {@code @property}. */
    PROPERTY_TAG,
    /** Java getter({@code getX}·{@code isX})·record 접근자의 Javadoc. */
    ACCESSOR,
    /** 속성·필드·생성자 파라미터 자신의 문서 주석. */
    DECLARATION
}
