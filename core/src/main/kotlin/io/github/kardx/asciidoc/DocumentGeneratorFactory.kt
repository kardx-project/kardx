package io.github.kardx.asciidoc

/**
 * 문서 클래스 하나의 [DocumentGenerator] 를 만드는 규칙. JUnit `AutoRestDocumentationExtension` 과 Kotest `RestDocsExtension` 의
 * 생성자 인자 `documentGenerator` 로 넘긴다.
 *
 * 문서를 쓸 때마다(문서 클래스가 끝날 때) 그 클래스의 테스트가 쓴 설정을 적용한 상태에서 부른다. 그래서 [DocumentGenerator] 의
 * 기본값(`document.includePrefix` 등)도 그 설정을 따른다.
 *
 * ```kotlin
 * DocumentGeneratorFactory {
 *     object : DocumentGenerator() {
 *         override fun config() = Config(toc = "right")
 *     }
 * }
 * ```
 */
public fun interface DocumentGeneratorFactory {
    /** [context] 문서에 쓸 생성기. */
    public fun create(context: DocumentGeneratorContext): DocumentGenerator

    /** 기본 생성기. */
    public companion object {
        /** 기본 [DocumentGenerator] 를 만든다. */
        public val DEFAULT: DocumentGeneratorFactory = DocumentGeneratorFactory { DocumentGenerator() }
    }
}

/**
 * [DocumentGeneratorFactory] 가 받는 입력. 라이브러리가 만들어 넘기며, 앞으로 정보가 더해질 수 있다.
 *
 * @property documentClass 문서 하나로 모이는 클래스 (JUnit 의 가장 바깥 테스트 클래스, Kotest 스펙 클래스)
 */
public class DocumentGeneratorContext internal constructor(public val documentClass: Class<*>) {
    override fun toString(): String = "DocumentGeneratorContext(${documentClass.name})"
}
