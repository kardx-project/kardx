package io.github.kardx.tooling.internal.sourcedocs;

/**
 * 설명 색인에 적을 항목 ({@code kardx { sourceDocs { … } }} 의 항목별 스위치). 끈 항목은 색인 JSON 에서 빠지므로
 * core 와 OpenAPI 후처리는 따로 확인하지 않아도 그 항목을 쓰지 않는다.
 *
 * @param fieldDocs 속성·record 구성 요소·getter 문서 → {@code types.<이름>.properties}
 * @param enumDocs enum 상수 문서 → {@code types.<이름>.enumConstants}
 * @param schemaDescriptions 클래스 문서 본문 → {@code types.<이름>.description} (OpenAPI 컴포넌트 스키마 설명)
 * @param paramDocs 핸들러 메서드의 {@code @param} → {@code methods.<키>.params}
 * @param methodDocs 핸들러 메서드 문서 본문 → {@code methods.<키>.summary}·{@code description}
 */
public record SourceDocsOptions(
    boolean fieldDocs,
    boolean enumDocs,
    boolean schemaDescriptions,
    boolean paramDocs,
    boolean methodDocs
) {
    /** 모든 항목을 적는다. */
    public static final SourceDocsOptions ALL = new SourceDocsOptions(true, true, true, true, true);

    /** 메서드 본문만 {@code methodDocs} 에 따르고 나머지는 모두 적는다 (DSL 기본값과 같은 꼴). */
    public static SourceDocsOptions withMethodDocs(boolean methodDocs) {
        return new SourceDocsOptions(true, true, true, true, methodDocs);
    }
}
