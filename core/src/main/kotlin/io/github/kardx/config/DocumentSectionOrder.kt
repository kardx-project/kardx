package io.github.kardx.config

/**
 * 생성 문서의 섹션(테스트 메서드)과 `@Nested` 클래스 순서의 기본값. 테스트 클래스에 JUnit `@TestMethodOrder`
 * (`MethodOrderer.OrderAnnotation`·`MethodName`·`DisplayName`)나 `@TestClassOrder` 가 있으면 그것을 먼저 따른다.
 * [io.github.kardx.config.DocumentSettings.sectionOrder], yml `kardx.document-section-order`.
 *
 * 소스의 선언 순서는 고를 수 없다. JVM 리플렉션이 메서드를 선언 순서로 돌려준다는 보장이 없어 실행마다 같은 결과를 낼 수 없기
 * 때문이다. 선언 순서대로 두려면 `@TestMethodOrder(MethodOrderer.OrderAnnotation::class)` 와 `@Order` 를 쓴다.
 */
public enum class DocumentSectionOrder {
    /** 메서드 이름순, `@Nested` 클래스는 클래스 이름순 (기본값). yml `method-name` */
    METHOD_NAME,

    /** `@DisplayName` 순 (없으면 메서드·클래스 이름). yml `display-name` */
    DISPLAY_NAME,
}
