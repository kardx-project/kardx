package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 지정한 요청 헤더를 OpenAPI 의 헤더 파라미터 대신 {@code apiKey}(in: header) 보안 스키마로 바꾼다.
 *
 * <p>Swagger UI 는 보안 스키마를 Authorize 창에서 한 번만 입력받아 모든 요청에 붙인다. {@code X-Tenant-Id} 처럼
 * 항상 보내는 헤더를 operation 마다 입력하지 않아도 되게 한다. epages 는 {@code Authorization} 만 보안 스키마로
 * 만들므로, 생성된 스펙 파일({@code openapi3.yaml}, {@code openapi3.json} 등 이름은 epages 설정을 따름)을 뒤에서 고친다.
 *
 * <p>헤더 이름은 두 곳에서 모은다: 테스트 설정({@code kardx.open-api.global-headers})이 스니펫 디렉터리에
 * 남긴 {@value #SETTINGS_FILE} 와 플러그인 확장 {@code openApiGlobalHeaders}.
 *
 * <p>스펙 파일 입출력은 {@link OpenApiSpecFiles}, YAML 형식은 {@link io.github.kardx.tooling.internal.spec.SwaggerYaml} 이 맡는다.
 */
public final class OpenApiGlobalHeaders {
    /** 테스트 JVM 이 설정을 남기는 파일 (스니펫 디렉터리 기준). 라이브러리의 OpenApiGlobalHeaders.FILE_NAME 과 같다. */
    static final String SETTINGS_FILE = "kardx/openapi-global-headers.json";
    private static final String HEADER = "header";
    private static final String IN = "in";
    private static final String NAME = "name";
    private static final String SECURITY = "security";

    private OpenApiGlobalHeaders() {
    }

    /** @return 바꾼 operation 수 */
    public static int apply(File snippetsDirectory, Collection<String> configured, File specDirectory) {
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(configured);
        headers.addAll(readSettings(new File(snippetsDirectory, SETTINGS_FILE)));
        headers.removeIf(String::isBlank);

        if (headers.isEmpty()) {
            return 0;
        }

        return OpenApiSpecFiles.rewriteOpenApi3(specDirectory, document -> transform(document, headers));
    }

    private static List<String> readSettings(File file) {
        if (!file.isFile()) {
            return List.of();
        }

        Object parsed = JsonFiles.read(file);

        if (!(parsed instanceof List<?> values)) {
            return List.of();
        }

        List<String> names = new ArrayList<>();

        for (Object name : values) {
            names.add(String.valueOf(name));
        }

        return names;
    }

    /**
     * 문서 트리를 고친다. 각 operation 의 헤더 파라미터 중 [headers] 에 있는 것을 빼고 그 이름의 보안 요구사항을 넣는다.
     * 보안 스키마는 {@code components.securitySchemes.<헤더 이름>} 에 추가한다.
     *
     * @return 바꾼 operation 수
     */
    static int transform(Map<String, Object> document, Set<String> headers) {
        Map<String, Object> schemes = new LinkedHashMap<>();
        int changed = 0;

        for (Map<String, Object> operation : SpecMaps.operations(document)) {
            if (convert(operation, headers, schemes)) {
                changed++;
            }
        }

        if (!schemes.isEmpty()) {
            Map<String, Object> components = SpecMaps.child(document, SpecMaps.COMPONENTS);
            Map<String, Object> securitySchemes = SpecMaps.child(components, SpecMaps.SECURITY_SCHEMES);

            schemes.forEach(securitySchemes::putIfAbsent);
        }

        return changed;
    }

    private static boolean convert(Map<String, Object> operation, Set<String> headers, Map<String, Object> schemes) {
        List<Object> parameters = SpecMaps.list(operation.get(SpecMaps.PARAMETERS));

        if (parameters == null) {
            return false;
        }

        List<String> matched = removeHeaders(parameters, headers, schemes);

        if (matched.isEmpty()) {
            return false;
        }

        if (parameters.isEmpty()) {
            operation.remove(SpecMaps.PARAMETERS);
        }

        requireSchemes(operation, matched);

        return true;
    }

    /** [headers] 에 있는 헤더 파라미터를 빼고 그 이름들을 돌려준다. 그 헤더의 보안 스키마는 [schemes] 에 모은다. */
    private static List<String> removeHeaders(
        List<Object> parameters, Set<String> headers, Map<String, Object> schemes) {
        List<String> matched = new ArrayList<>();
        Iterator<Object> iterator = parameters.iterator();

        while (iterator.hasNext()) {
            Map<String, Object> parameter = SpecMaps.map(iterator.next());
            String name = globalHeaderName(parameter, headers);

            if (name != null) {
                iterator.remove();
                matched.add(name);
                schemes.computeIfAbsent(name, n -> scheme(n, parameter.get(SpecMaps.DESCRIPTION)));
            }
        }

        return matched;
    }

    /** [headers] 에 있는 헤더 파라미터면 그 이름, 아니면 null. */
    private static String globalHeaderName(Map<String, Object> parameter, Set<String> headers) {
        if (parameter == null || !HEADER.equals(parameter.get(IN))) {
            return null;
        }

        String name = String.valueOf(parameter.get(NAME));

        return headers.contains(name) ? name : null;
    }

    /** security 목록의 각 항목은 함께 요구하는 스키마 묶음(AND)이다. 기존 Bearer 등과 함께 요구하도록 모든 항목에 넣는다. */
    private static void requireSchemes(Map<String, Object> operation, List<String> names) {
        List<Object> security = SpecMaps.list(operation.get(SECURITY));

        if (security == null || security.isEmpty()) {
            security = new ArrayList<>(List.of(new LinkedHashMap<String, Object>()));
            operation.put(SECURITY, security);
        }

        for (Map<String, Object> requirement : SpecMaps.maps(security)) {
            names.forEach(name -> requirement.putIfAbsent(name, new ArrayList<>()));
        }
    }

    private static Map<String, Object> scheme(String name, Object description) {
        Map<String, Object> scheme = new LinkedHashMap<>();
        scheme.put(SpecMaps.TYPE, "apiKey");
        scheme.put(IN, HEADER);
        scheme.put(NAME, name);

        if (description != null && !String.valueOf(description).isBlank()) {
            scheme.put(SpecMaps.DESCRIPTION, description);
        }

        return scheme;
    }
}
