package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.openapi.OpenApiResources;
import io.github.kardx.tooling.internal.openapi.OpenApiSchemaNames;
import io.github.kardx.tooling.internal.openapi.SecurityRequirementsAligner;
import java.io.File;
import java.util.List;

/**
 * OpenAPI 생성기(epages)에 줄 스니펫 복사본. 테스트 출력(스니펫)을 고치지 않으려고 OpenAPI 조각만 복사한 뒤 복사본에서
 * 같은 API 테스트 사이 보안 요구사항을 맞추고, 이름이 같고 구조가 다른 스키마를 정리한다.
 */
public final class OpenApiSnippets {
    /** 복사할 파일 (스니펫 디렉터리 기준 Ant 패턴). */
    public static final List<String> PATTERNS = List.of(
        "**/" + OpenApiSchemaNames.RESOURCE_FILE,
        "**/" + OpenApiSchemaNames.METADATA_FILE,
        "**/" + OpenApiResources.EXTRAS_FILE,
        "kardx/**");

    private OpenApiSnippets() {
    }

    /** {@code snippets} 의 OpenAPI 조각을 {@code copy} 로 복사하고({@link #PATTERNS}) {@link #prepare} 한다. */
    public static void copy(File snippets, File copy, ToolLog log) {
        FileTrees.sync(snippets, PATTERNS, copy);
        prepare(copy, log);
    }

    /** 복사본의 보안 요구사항을 맞추고 스키마 이름을 정리한다. */
    public static void prepare(File copy, ToolLog log) {
        int aligned = SecurityRequirementsAligner.align(copy);

        if (aligned > 0) {
            log.info("OpenAPI: aligned security requirements in {} snippets", aligned);
        }

        OpenApiSchemaNames.Result names = OpenApiSchemaNames.resolve(copy);

        names.changes().forEach(change -> log.lifecycle("OpenAPI schema names: {}", change));
        names.warnings().forEach(warning -> log.warn("OpenAPI schema names: {}", warning));
    }
}
