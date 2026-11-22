package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.PARAMETERS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUIRED;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.TYPE;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;

/**
 * Spring Framework 7 API 버전({@value OpenApiResources#EXTRAS_FILE} 의 {@code apiVersion})을 operation 에 적는다.
 * <ul>
 *   <li>{@value #EXTENSION}: 버전</li>
 *   <li>{@code apiVersioning { header(...) }}·{@code queryParameter(...)} 면 그 버전만 받는 필수 파라미터
 *       ({@code enum}·{@code example} 이 버전). 테스트가 문서화한 같은 파라미터가 있으면 그것을 고친다</li>
 *   <li>같은 경로·메서드를 여러 버전으로 문서화했으면 가장 높은 버전을 적는다. epages 가 그 조각들을 operation 하나로 합쳤으므로
 *       본문 스키마는 모든 버전의 합이다. {@code splitSpecs} 가 꺼져 있으면 가려진 버전을 경고한다
 *       (켜져 있으면 {@link OpenApiVersionSplit} 이 버전별 스펙을 만든다)</li>
 * </ul>
 */
final class OpenApiApiVersions {
    /** operation 의 API 버전을 적는 확장 키. */
    static final String EXTENSION = "x-api-version";
    private static final String NAME = "name";
    private static final String IN = "in";

    private OpenApiApiVersions() {
    }

    /**
     * 문서 트리를 고친다.
     *
     * @param warnings 여러 버전 중 하나만 남긴 operation 을 여기에 더한다
     * @return 버전을 적은 operation 수
     */
    static int transform(Map<String, Object> document, List<Resource> resources, ApiVersioningSettings settings,
        List<String> warnings) {
        Map<String, SortedSet<String>> versions = ApiVersions.byOperation(resources);
        int changed = 0;

        for (Resource resource : resources) {
            SortedSet<String> documented = versions.remove(ApiVersions.key(resource.method(), resource.path()));
            Map<String, Object> operation = resource.operation(document);

            if (documented == null || operation == null) {
                continue;
            }

            mark(operation, documented.last(), settings);
            changed++;

            if (documented.size() > 1 && !settings.splitSpecs()) {
                warnings.add(resource.method().toUpperCase(Locale.ROOT) + " " + resource.path()
                    + " is documented for API versions " + documented + "; the spec shows " + documented.last()
                    + " only (hidden: " + documented.headSet(documented.last()) + "). Set "
                    + "kardx.apiVersioning.splitSpecs = true for one spec per version");
            }
        }

        return changed;
    }

    /** [operation] 에 [version] 을 적는다: {@value #EXTENSION} 과 (헤더·쿼리 방식이면) 필수 파라미터. */
    static void mark(Map<String, Object> operation, String version, ApiVersioningSettings settings) {
        operation.put(EXTENSION, version);

        String in = settings.parameterLocation();

        if (in == null) {
            return;
        }

        Map<String, Object> parameter = existingParameter(operation, in, settings.name());

        if (parameter == null) {
            parameter = new LinkedHashMap<>();
            parameter.put(NAME, settings.name());
            parameter.put(IN, in);
            SpecMaps.childList(operation, PARAMETERS).add(parameter);
        }

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put(TYPE, "string");
        schema.put("enum", new ArrayList<>(List.of(version)));
        parameter.put(REQUIRED, true);
        parameter.put(SCHEMA, schema);
        parameter.put(EXAMPLE, version);
    }

    /** 이미 있는 같은 파라미터. 헤더 이름은 대소문자를 가리지 않는다. */
    private static Map<String, Object> existingParameter(Map<String, Object> operation, String in, String name) {
        boolean header = ApiVersioningSettings.HEADER.equals(in);

        return SpecMaps.maps(operation.get(PARAMETERS))
            .stream()
            .filter(p -> in.equals(p.get(IN)))
            .filter(p -> header ? name.equalsIgnoreCase(String.valueOf(p.get(NAME))) : name.equals(p.get(NAME)))
            .findFirst()
            .orElse(null);
    }
}
