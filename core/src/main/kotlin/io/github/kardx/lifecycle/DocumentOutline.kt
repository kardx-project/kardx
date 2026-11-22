package io.github.kardx.lifecycle

import io.github.kardx.internal.output.DisplayNames
import io.github.kardx.internal.output.asciiDescription

/**
 * 생성할 AsciiDoc 문서 하나의 구조: 제목과 순서 있는 섹션 목록. [DocumentationLifecycle.writeDocument] 가 받아
 * `<document-class>.adoc` 을 쓴다.
 *
 * 테스트 프레임워크 확장이 만든다. JUnit 확장은 테스트 클래스의 메서드를 리플렉션으로 읽어 만들고, Kotest 확장은
 * 실제로 실행된 리프 테스트를 순서대로 기록해 만든다.
 *
 * ```kotlin
 * DocumentOutline(
 *     documentClass = UserApiDocsSpec::class.java,
 *     sections = listOf(DocumentSection("get-user", "사용자 조회")),
 * )
 * ```
 *
 * @property documentClass 문서 하나로 모이는 클래스. 문서 파일 이름(`user-api-docs-spec.adoc`)과 스니펫 디렉터리의
 *   첫 단계(`user-api-docs-spec/`)가 이 클래스의 단순 이름을 kebab-case 로 바꾼 값이다
 * @property sections 문서 섹션. 이 순서대로 쓴다. 스니펫이 없는 섹션은 건너뛴다
 * @property title 문서 제목 (`= 제목`). 기본값은 클래스의 `@DisplayName`(JUnit 또는 Kotest), 없으면 클래스 단순 이름
 * @property description 제목 뒤에 붙는 설명. 기본값은 클래스의 `@AsciiDescription`
 */
@RestDocsExtensionApi
public class DocumentOutline(
    public val documentClass: Class<*>,
    public val sections: List<DocumentSection>,
    public val title: String = DisplayNames.titleOf(documentClass),
    public val description: String? = documentClass.asciiDescription(),
) {
    override fun toString(): String = "DocumentOutline(documentClass=${documentClass.name}, title=$title, " +
        "description=$description, sections=$sections)"
}
