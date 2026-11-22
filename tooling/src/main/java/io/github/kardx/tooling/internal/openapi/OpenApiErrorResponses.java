package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.openapi.OpenApiCommonResponses.ErrorBody;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 테스트가 만들지 않은 응답을 더한다: 테스트가 선언한 응답({@link OpenApiDeclaredResponses})을 먼저, 그다음 플러그인 확장으로
 * 정한 공통 응답({@link OpenApiCommonResponses})을. 먼저 더한 응답은 문서화된 상태 코드가 되므로 선언이 공통 응답을 이긴다.
 * 두 단계는 같은 본문 기본값({@link OpenApiCommonResponses#errorBody})을 쓰고, 그 값은 선언한 응답을 더하기 전에 정하므로
 * 선언한 스키마가 공통 응답의 본문 선택에 끼어들지 않는다.
 */
public final class OpenApiErrorResponses {
    private OpenApiErrorResponses() {
    }

    /**
     * 후처리 결과.
     *
     * @param declared 더한 선언 응답 수 (operation × 상태 코드)
     * @param common 더한 공통 응답 수 (operation × 상태 코드)
     * @param schema 공통 응답 본문이 가리킨 스키마 이름. 설명만 넣었거나 공통 응답이 없으면 null
     * @param declaredWarnings 선언 응답 경고 (같은 상태 코드를 다른 설명으로 선언)
     * @param commonWarnings 공통 응답 경고 (정한 스키마가 스펙에 없음)
     */
    public record Result(
        int declared, int common, String schema, List<String> declaredWarnings, List<String> commonWarnings) {
    }

    /**
     * {@code specDirectory} 안의 OpenAPI 3 스펙에 선언 응답과 공통 응답을 더한다.
     *
     * @param snippetsDirectory 라이브러리가 남긴 조각 ({@value OpenApiResources#EXTRAS_FILE})의 디렉터리
     * @param configured 공통 응답 (상태 코드 → 설명). 비면 공통 응답을 더하지 않는다
     * @param schema 본문 스키마 이름. null 이면 스펙에서 찾는다 ({@link OpenApiCommonResponses#errorBody})
     */
    public static Result apply(
        File snippetsDirectory, File specDirectory, Map<Integer, String> configured, String schema) {
        Map<Integer, String> descriptions = OpenApiCommonResponses.descriptions(configured);
        List<Resource> resources = OpenApiResources.read(snippetsDirectory);
        List<String> declaredWarnings = new ArrayList<>();
        List<String> commonWarnings = new ArrayList<>();
        int[] declared = {0};
        String[] used = {null};
        int added = OpenApiSpecFiles.rewriteOpenApi3(specDirectory, document -> {
            List<String> bodyWarnings = new ArrayList<>();
            ErrorBody body = OpenApiCommonResponses.errorBody(document, schema, bodyWarnings);
            int count = OpenApiDeclaredResponses.transform(document, resources, body, declaredWarnings);

            declared[0] += count;

            if (!descriptions.isEmpty()) {
                commonWarnings.addAll(bodyWarnings);
                used[0] = body.schema();
                count += OpenApiCommonResponses.transform(document, descriptions, body);
            }

            return count;
        });

        return new Result(declared[0], added - declared[0], used[0], declaredWarnings, commonWarnings);
    }
}
