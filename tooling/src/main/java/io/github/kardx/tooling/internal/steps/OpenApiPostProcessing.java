package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.openapi.ApiVersioningSettings;
import io.github.kardx.tooling.internal.openapi.OpenApiBodySchemas;
import io.github.kardx.tooling.internal.openapi.OpenApiErrorResponses;
import io.github.kardx.tooling.internal.openapi.OpenApiGlobalHeaders;
import io.github.kardx.tooling.internal.openapi.OpenApiInfoSettings;
import io.github.kardx.tooling.internal.openapi.OpenApiMetadata;
import io.github.kardx.tooling.internal.openapi.OpenApiRefinements;
import io.github.kardx.tooling.internal.openapi.OpenApiSecuritySettings;
import io.github.kardx.tooling.internal.openapi.OpenApiVersionSplit;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * epages 가 만든 OpenAPI 3 스펙(스펙 디렉터리의 openapi3 파일)을 이 라이브러리가 남긴 정보로 고친다. 순서: 전역 헤더 → apiKey,
 * JSON 이 아닌 본문 스키마 → 문자열, 타입·제약·multipart·쿠키·다형성 보강, 선언·공통 응답, 문서 메타데이터·클래스 KDoc 스키마 설명,
 * 버전별 스펙. Gradle 플러그인은 {@code openapi3} 태스크 뒤에, Maven 플러그인·명령줄은 기본 스펙을 만든 뒤에 부른다.
 */
public final class OpenApiPostProcessing {
    private OpenApiPostProcessing() {
    }

    /**
     * 후처리 설정.
     *
     * @param globalHeaders apiKey 보안 스키마로 바꿀 헤더 이름
     * @param commonResponses 모든 operation 에 더할 응답 (상태 → 설명)
     * @param commonResponseSchema 공통 응답 본문 스키마 이름. 없으면 null
     * @param info {@code openApiInfo} 문서 메타데이터
     * @param sourceDocs 클래스 KDoc 설명 색인 ({@code source-docs.json}). 없으면 null
     */
    public record Settings(
        Set<String> globalHeaders, Map<Integer, String> commonResponses, String commonResponseSchema,
        DocsLanguage language, OpenApiSecuritySettings security, ApiVersioningSettings versioning,
        OpenApiInfoSettings info, File sourceDocs) {
    }

    /**
     * @param snippets 스니펫 복사본 ({@link OpenApiSnippets})
     * @param specDir openapi3 스펙이 있는 디렉터리
     * @return 새로 쓴 버전별 스펙 파일 ({@code apiVersioning.splitSpecs})
     */
    public static List<File> apply(File snippets, File specDir, Settings settings, ToolLog log) {
        postProcess(snippets, specDir, settings, log);
        addErrorResponses(snippets, specDir, settings, log);
        addMetadata(snippets, specDir, settings, log);

        List<File> written = OpenApiVersionSplit.apply(snippets, specDir, settings.versioning());

        written.forEach(file -> log.info("OpenAPI: wrote version spec {}", file));

        return written;
    }

    private static void postProcess(File snippets, File specDir, Settings settings, ToolLog log) {
        int changed = OpenApiGlobalHeaders.apply(snippets, settings.globalHeaders(), specDir);

        if (changed > 0) {
            log.lifecycle("OpenAPI: turned global headers into apiKey security schemes ({} operations)", changed);
        }

        int bodies = OpenApiBodySchemas.apply(specDir);

        if (bodies > 0) {
            log.info("OpenAPI: replaced {} non-JSON body schemas with string", bodies);
        }

        OpenApiRefinements.Result refined = OpenApiRefinements.apply(snippets, specDir, settings.language(),
            settings.security(), settings.versioning());

        log.info("OpenAPI: added {} type and constraint hints, {} multipart, cookie, value, file and polymorphic "
            + "body details", refined.hints(), refined.extras());
        refined.operationIdWarnings().forEach(warning -> log.warn("OpenAPI operationId: {}", warning));
        refined.securityWarnings().forEach(warning -> log.warn("OpenAPI security: {}", warning));
        refined.versionWarnings().forEach(warning -> log.warn("OpenAPI API versions: {}", warning));
        refined.textWarnings().forEach(warning -> log.warn("OpenAPI summary/description: {}", warning));
    }

    /** 보안 요구사항이 정해진 뒤(전역 헤더 변환 뒤)에 테스트가 선언한 응답과 공통 응답을 더한다. */
    private static void addErrorResponses(File snippets, File specDir, Settings settings, ToolLog log) {
        OpenApiErrorResponses.Result result = OpenApiErrorResponses.apply(snippets, specDir,
            settings.commonResponses(), settings.commonResponseSchema());

        result.declaredWarnings().forEach(warning -> log.warn("OpenAPI declared responses: {}", warning));
        result.commonWarnings().forEach(warning -> log.warn("OpenAPI common responses: {}", warning));

        if (result.declared() > 0) {
            log.info("OpenAPI: added {} declared responses", result.declared());
        }

        if (result.common() > 0) {
            log.info("OpenAPI: added {} common responses (body schema: {})", result.common(),
                result.schema() == null ? "none" : result.schema());
        }
    }

    /** 문서 메타데이터({@code openApiInfo})와 클래스 KDoc 스키마 설명을 넣는다 ({@link OpenApiMetadata}). */
    private static void addMetadata(File snippets, File specDir, Settings settings, ToolLog log) {
        OpenApiMetadata.Result result = OpenApiMetadata.apply(snippets, specDir, settings.info(),
            settings.sourceDocs(), settings.language());

        result.warnings().forEach(warning -> log.warn("OpenAPI info: {}", warning));
        log.info("OpenAPI: set {} info and tag items, described {} schemas from class KDoc", result.info(),
            result.schemaDescriptions());
    }
}
