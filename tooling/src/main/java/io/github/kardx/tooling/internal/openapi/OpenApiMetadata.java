package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 스펙 후처리의 마지막 단계: 문서 메타데이터({@code openApiInfo { }}, {@link OpenApiInfoMetadata})와 클래스 KDoc 으로 만든
 * 컴포넌트 스키마 설명({@link SchemaDescriptions})을 넣는다. 태그 목록이 정해진 뒤({@link OpenApiRefinements}),
 * 버전별 스펙을 만들기 전({@link OpenApiVersionSplit})에 돈다.
 */
public final class OpenApiMetadata {
    private OpenApiMetadata() {
    }

    /**
     * 결과.
     *
     * @param info 넣거나 바꾼 메타데이터 항목 수
     * @param schemaDescriptions 설명을 넣은 컴포넌트 스키마 수
     * @param warnings 넣지 못한 설정 (operation 이 없는 태그, 필수 값이 없는 license·externalDocs)
     */
    public record Result(int info, int schemaDescriptions, List<String> warnings) {
    }

    /**
     * @param sourceDocs 설명 색인 ({@code source-docs.json}). {@code sourceDocs.enabled = false} 면 null
     * @param language {@code x-tagGroups} 의 나머지 그룹 이름 언어
     */
    public static Result apply(File snippetsDirectory, File specDirectory, OpenApiInfoSettings settings,
        File sourceDocs, DocsLanguage language) {
        Map<String, String> descriptions = SchemaDescriptions.read(snippetsDirectory, sourceDocs);
        List<String> warnings = new ArrayList<>();
        int info = 0;
        int schemas = 0;

        for (File spec : OpenApiSpecFiles.list(specDirectory)) {
            Map<String, Object> document = OpenApiSpecFiles.readOpenApi3(spec);

            if (document == null) {
                continue;
            }

            int changed = OpenApiInfoMetadata.transform(document, settings, language, warnings);
            int described = SchemaDescriptions.transform(document, descriptions);

            if (changed + described > 0) {
                OpenApiSpecFiles.write(spec, document);
            }

            info += changed;
            schemas += described;
        }

        return new Result(info, schemas, warnings.stream()
            .distinct()
            .toList());
    }
}
