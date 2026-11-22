package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.PATHS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * epages 가 만든 operation 의 세부를 다듬는다.
 * <ul>
 *   <li>응답 {@code description} 이 상태 코드({@code "404"})면 reason phrase 로. 그 응답을 만든 테스트의 summary 가
 *       operation 의 summary 와 다르면 덧붙인다 ({@code Not Found: 상품 조회 - 없는 상품})</li>
 *   <li>본문 예시 ({@link OpenApiExamples})</li>
 *   <li>operationId ({@link OpenApiOperationIds})</li>
 *   <li>최상위 {@code tags} 와 {@code security} ({@link OpenApiDocumentTidy})</li>
 * </ul>
 */
final class OpenApiOperationTidy {
    private static final String SUMMARY = "summary";

    private OpenApiOperationTidy() {
    }

    /** @return operationId 경고 ({@link OpenApiOperationIds#operationIds}) */
    static List<String> transform(Map<String, Object> document, List<Resource> resources) {
        Map<String, List<Resource>> byOperation = new LinkedHashMap<>();
        Map<String, Resource> byId = new LinkedHashMap<>();

        for (Resource resource : resources) {
            byOperation.computeIfAbsent(resource.path() + " " + resource.method(), k -> new ArrayList<>())
                .add(resource);
            byId.putIfAbsent(resource.operationId(), resource);
        }

        Map<String, Object> paths = map(document, PATHS);

        if (paths != null) {
            paths.forEach((path, item) -> {
                for (String method : OpenApiSpecFiles.OPERATION_METHODS) {
                    Map<String, Object> operation = map(map(item), method);

                    if (operation != null) {
                        List<Resource> merged = byOperation.getOrDefault(path + " " + method, List.of());

                        primarySummary(operation, merged);
                        OpenApiOperationIds.mergedOperationId(operation, merged);
                        responseDescriptions(operation, merged);
                        OpenApiExamples.examples(operation, byId);
                    }
                }
            });
        }

        OpenApiDocumentTidy.explicitSecurity(document);
        List<String> warnings = OpenApiOperationIds.operationIds(document, resources);
        OpenApiDocumentTidy.tags(document, resources);

        return warnings;
    }

    /**
     * 여러 테스트가 합쳐진 operation 의 summary·description 을 성공 응답 테스트에서 가져온다. epages 0.19 는 처음 읽은 조각,
     * 0.20 은 성공 응답 조각을 쓰므로 버전·실행 순서와 상관없이 같게 맞춘다 (200, 201, 202, 204, 그 밖의 2xx, 나머지 순).
     */
    private static void primarySummary(Map<String, Object> operation, List<Resource> resources) {
        resources.stream()
            .filter(r -> r.summary() != null && !r.summary().isBlank())
            .min(PrimaryResources.PRIMARY_FIRST)
            .ifPresent(primary -> {
                String description = SpecMaps.string(primary.json(), DESCRIPTION);
                boolean blank = description == null || description.isBlank();

                operation.put(SUMMARY, primary.summary());
                operation.put(DESCRIPTION, blank ? primary.summary() : description);
            });
    }

    private static void responseDescriptions(Map<String, Object> operation, List<Resource> resources) {
        Map<String, Object> responses = map(operation, RESPONSES);

        if (responses == null) {
            return;
        }

        responses.forEach((code, value) -> {
            Map<String, Object> response = map(value);

            if (response != null && code.equals(SpecMaps.string(response, DESCRIPTION))) {
                response.put(DESCRIPTION, describe(code, operation, resources));
            }
        });
    }

    private static String describe(String code, Map<String, Object> operation, List<Resource> resources) {
        int status = parseStatus(code);
        String phrase = HttpStatusPhrases.of(status);
        String summary = resources.stream()
            .filter(r -> r.status() == status)
            .map(Resource::summary)
            .filter(s -> s != null && !s.isBlank())
            .findFirst()
            .orElse(null);

        if (phrase == null) {
            return summary == null ? code : summary;
        }

        boolean sameAsOperation = summary == null || summary.equals(SpecMaps.string(operation, SUMMARY));

        return sameAsOperation ? phrase : phrase + ": " + summary;
    }

    private static int parseStatus(String code) {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            // "default", "2XX" 처럼 숫자가 아닌 응답 키는 reason phrase 가 없다
            return -1;
        }
    }
}
