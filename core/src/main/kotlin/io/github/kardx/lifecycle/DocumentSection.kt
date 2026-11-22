package io.github.kardx.lifecycle

/**
 * 문서 섹션 하나. 테스트 실행 하나가 만든 스니펫을 모은 `== 제목` 블록이다.
 *
 * @property snippetName 스니펫 디렉터리의 테스트 자리 이름. [DocumentationLifecycle.startTest] 에 넘긴 이름과 같다.
 *   실제 디렉터리는 REST Docs 의 `{class-name}/{method-name}` 규칙으로 정해진다 (`user-api-docs-spec/get-user`).
 *   섹션 앵커(와 `test-name` 규칙의 OpenAPI operationId)도 이 이름에서 나온다
 * @property title 섹션 제목
 * @property description 섹션 제목 뒤에 붙는 설명. 없으면 null
 */
@RestDocsExtensionApi
public class DocumentSection(
    public val snippetName: String,
    public val title: String,
    public val description: String? = null,
) {
    override fun toString(): String =
        "DocumentSection(snippetName=$snippetName, title=$title, description=$description)"
}
