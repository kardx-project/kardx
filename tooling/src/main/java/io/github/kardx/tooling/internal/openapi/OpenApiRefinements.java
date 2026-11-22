package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * epages 가 만든 OpenAPI 3 스펙을 라이브러리가 남긴 정보로 보강한다. 스펙 파일마다 한 번 읽고 한 번 쓴다.
 * 순서: 민감 헤더의 요청 값 예시 빼기({@link SensitiveHeaderExamples}) → 스키마 정보({@link OpenApiSchemaHints}) →
 * 본문 필드 기본값({@link OpenApiFieldDefaults}) → epages 밖의 요청 정보({@link OpenApiRequestExtras}) → 파일 본문
 * ({@link OpenApiFileBodies}) → 다형 본문({@link OpenApiPolymorphism}) → OAuth2·OpenID Connect 보안
 * ({@link OpenApiOAuthSecurity}) → API 버전({@link OpenApiApiVersions}) → operation 다듬기({@link OpenApiOperationTidy}) →
 * 테스트가 정한·소스 문서의 summary·description({@link OpenApiOperationTexts}).
 * 입력을 이름순으로 읽으므로 테스트 실행 순서와 상관없이 같은 결과가 나온다.
 */
public final class OpenApiRefinements {
    private OpenApiRefinements() {
    }

    /**
     * 보강 결과.
     *
     * @param hints 타입·제약·기본값·예시를 합친 항목 수
     * @param extras multipart·쿠키·값 본문 스키마·파일 본문·다형 본문 정보를 넣은 operation(자리) 수
     * @param operationIdWarnings operationId 경고 (직접 정한 id 가 겹침 등)
     * @param securityWarnings 보안 경고 (정하지 않은 보안 스키마를 쓴 operation)
     * @param versionWarnings API 버전 경고 (여러 버전 중 하나만 남긴 operation)
     * @param textWarnings summary·description 경고 (같은 operation 의 테스트가 서로 다른 값을 정함)
     */
    public record Result(int hints, int extras, List<String> operationIdWarnings, List<String> securityWarnings,
        List<String> versionWarnings, List<String> textWarnings) {
    }

    /**
     * @param language 설명에 붙이는 문구(제약, 쿠키 목록)의 언어
     * @param security {@code openApiSecurity { }} 값
     * @param versioning {@code apiVersioning { }} 값
     */
    public static Result apply(File snippetsDirectory, File specDirectory, DocsLanguage language,
        OpenApiSecuritySettings security, ApiVersioningSettings versioning) {
        List<Resource> resources = OpenApiResources.read(snippetsDirectory);
        int hints = 0;
        int extras = 0;
        List<String> warnings = new ArrayList<>();
        List<String> securityWarnings = new ArrayList<>();
        List<String> versionWarnings = new ArrayList<>();
        List<String> textWarnings = new ArrayList<>();

        for (File spec : OpenApiSpecFiles.list(specDirectory)) {
            Map<String, Object> document = OpenApiSpecFiles.readOpenApi3(spec);

            if (document == null) {
                continue;
            }

            SensitiveHeaderExamples.remove(document);
            hints += OpenApiSchemaHints.transform(document, resources, language);
            hints += OpenApiFieldDefaults.transform(document, resources);
            extras += OpenApiRequestExtras.transform(document, resources, language);
            extras += OpenApiFileBodies.transform(document, resources);
            extras += OpenApiPolymorphism.transform(document, resources);
            extras += OpenApiOAuthSecurity.transform(document, resources, security, securityWarnings);
            OpenApiApiVersions.transform(document, resources, versioning, versionWarnings);
            warnings.addAll(OpenApiOperationTidy.transform(document, resources));
            OpenApiOperationTexts.transform(document, resources, textWarnings);
            OpenApiSpecFiles.write(spec, document);
        }

        return new Result(hints, extras, warnings, distinct(securityWarnings), distinct(versionWarnings),
            distinct(textWarnings));
    }

    /** 스펙 파일이 여럿이면 같은 경고가 되풀이되므로 한 번씩만. */
    private static List<String> distinct(List<String> warnings) {
        return warnings.stream()
            .distinct()
            .toList();
    }
}
