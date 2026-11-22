package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * operation 의 summary·description 을 넣는다. 우선 순위: 테스트가 {@code openApi { summary = …; description = … }} 로 정한
 * 값({@value OpenApiResources#EXTRAS_FILE} 의 {@code summary}·{@code description}) > 컨트롤러 메서드의 KDoc·Javadoc
 * ({@code sourceSummary}·{@code sourceDescription}, {@code apiDocsSourceDocs} 색인에서 라이브러리가 남긴다) > 기본값.
 * <ul>
 *   <li>epages 가 조각을 합치고 {@link OpenApiOperationTidy} 가 대표 테스트의 값을 넣은 뒤에 덮어쓴다. 응답 설명
 *       ({@code Created: 카드 결제})은 테스트 이름 기준 그대로다</li>
 *   <li>같은 operation 의 조각이 서로 다른 값을 정하면 스니펫 디렉터리 이름순으로 처음 것을 쓰고 경고한다. 소스 문서 값은
 *       같은 메서드에서 오므로 경고하지 않고 처음 것을 쓴다</li>
 *   <li>정한 summary 만 있고 description 이 summary 를 대신 쓰고 있었으면(테스트 설명이 없음) description 도 바꾼다.
 *       소스 문서 description 이 있으면 그것이 먼저다</li>
 * </ul>
 * 버전별 스펙({@link OpenApiVersionSplit})은 이 결과를 복사하므로 같은 값이 들어간다.
 */
final class OpenApiOperationTexts {
    private static final String SUMMARY = "summary";
    private static final String SOURCE_SUMMARY = "sourceSummary";
    private static final String SOURCE_DESCRIPTION = "sourceDescription";

    private OpenApiOperationTexts() {
    }

    /**
     * @param warnings 같은 operation 의 조각이 서로 다른 값을 정했을 때의 경고를 더한다
     */
    static void transform(Map<String, Object> document, List<Resource> resources, List<String> warnings) {
        Map<String, List<Resource>> byOperation = new LinkedHashMap<>();

        resources.stream()
            .sorted(Comparator.comparing(Resource::id))
            .forEach(r -> byOperation.computeIfAbsent(r.method() + " " + r.path(), k -> new ArrayList<>()).add(r));

        byOperation.values().forEach(merged -> {
            Resource first = merged.get(0);
            Map<String, Object> operation = first.operation(document);

            if (operation != null) {
                apply(first.method().toUpperCase(Locale.ROOT) + " " + first.path(), operation, merged, warnings);
            }
        });
    }

    private static void apply(String label, Map<String, Object> operation, List<Resource> merged,
        List<String> warnings) {
        String summary = declared(label, SUMMARY, merged, warnings);
        String description = declared(label, DESCRIPTION, merged, warnings);
        boolean summaryAsDescription = Objects.equals(operation.get(DESCRIPTION), operation.get(SUMMARY));

        if (summary == null) {
            summary = first(SOURCE_SUMMARY, merged);
        }

        if (description == null) {
            description = first(SOURCE_DESCRIPTION, merged);
        }

        if (description == null && summary != null && summaryAsDescription) {
            description = summary;
        }

        if (summary != null) {
            operation.put(SUMMARY, summary);
        }

        if (description != null) {
            operation.put(DESCRIPTION, description);
        }
    }

    /** 조각들의 {@code name} 값 중 처음 것 (경고 없음). 없으면 null. */
    private static String first(String name, List<Resource> merged) {
        return merged.stream()
            .map(resource -> SpecMaps.string(resource.extras(), name))
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
    }

    /** 조각들이 정한 {@code name} 값 중 처음 것. 정한 조각이 없으면 null. 값이 여럿이면 {@code label} 을 넣어 경고한다. */
    private static String declared(String label, String name, List<Resource> merged, List<String> warnings) {
        Set<String> values = new LinkedHashSet<>();

        for (Resource resource : merged) {
            String value = SpecMaps.string(resource.extras(), name);

            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        String first = values.iterator().next();

        if (values.size() > 1) {
            warnings.add(label + ": tests declare different " + name + " values "
                + values.stream().map(v -> "'" + v + "'").toList()
                + "; using '" + first + "' (first in snippet directory order)");
        }

        return first;
    }
}
