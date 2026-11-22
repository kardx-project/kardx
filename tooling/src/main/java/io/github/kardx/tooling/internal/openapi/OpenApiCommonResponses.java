package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 플러그인 확장 {@code openApiCommonResponses} 로 선언한 공통 응답(401, 403, 500 등)을 모든 OpenAPI operation 에 더한다.
 *
 * <ul>
 *   <li>operation 이 이미 문서화한 상태 코드는 건드리지 않는다 (테스트가 만든 응답이 이긴다)</li>
 *   <li>{@code 401}·{@code 403} 은 보안 요구사항이 있는 operation 에만 더한다. operation 의 {@code security} 가 없으면 문서
 *       최상위 {@code security} 를 보고, 빈 요구사항({@code {}}, 인증 선택)만 있으면 보안이 없는 것으로 본다</li>
 *   <li>응답 본문은 공통 오류 스키마를 가리킨다({@link #errorBody}). 없으면 설명만 넣는다</li>
 *   <li>더한 operation 의 응답은 상태 코드 순서로 다시 놓는다 (숫자 → {@code 4XX} 같은 범위 → {@code default})</li>
 * </ul>
 * 테스트가 선언한 응답({@link OpenApiDeclaredResponses})을 먼저 더하므로 선언이 공통 응답을 이긴다 ({@link OpenApiErrorResponses}).
 */
public final class OpenApiCommonResponses {
    private static final Set<Integer> AUTH_STATUSES = Set.of(401, 403);
    private static final int MIN_STATUS = 100;
    private static final int MAX_STATUS = 599;
    private static final int ERROR_STATUS = 400;
    private static final String SECURITY = "security";
    /** 본문 미디어 타입을 알 수 없을 때 쓰는 값. */
    static final String DEFAULT_MEDIA_TYPE = "application/json";
    private static final Pattern NUMERIC = Pattern.compile("\\d{3}");
    private static final Pattern RANGE = Pattern.compile("[1-5]XX");
    private static final Pattern ERROR_RANGE = Pattern.compile("[45]XX");

    private OpenApiCommonResponses() {
    }

    /** 응답 본문: 가리킬 스키마 이름(없으면 null)과 미디어 타입. */
    record ErrorBody(String schema, String mediaType) {
    }

    /**
     * 사용자가 정한 상태 코드·설명을 확인하고 상태 코드 순서로 돌려준다. 빈 설명은 표준 reason phrase 로 바꾼다.
     *
     * @throws ToolingException 상태 코드가 100~599 밖이면
     */
    public static Map<Integer, String> descriptions(Map<Integer, String> configured) {
        Map<Integer, String> descriptions = new TreeMap<>();

        configured.forEach((status, description) -> {
            if (status == null || status < MIN_STATUS || status > MAX_STATUS) {
                throw new ToolingException("kardx.openApiCommonResponses: status must be between 100 and 599, "
                    + "got " + status);
            }

            descriptions.put(status, description == null || description.isBlank() ? phrase(status) : description);
        });

        return descriptions;
    }

    /** 문서 트리를 고친다. @return 더한 응답 수 */
    static int transform(Map<String, Object> document, Map<Integer, String> descriptions, ErrorBody body) {
        List<Object> rootSecurity = SpecMaps.listOrEmpty(document.get(SECURITY));
        int added = 0;

        for (Map<String, Object> operation : SpecMaps.operations(document)) {
            added += addResponses(operation, descriptions, body, isSecured(operation, rootSecurity));
        }

        return added;
    }

    private static int addResponses(
        Map<String, Object> operation, Map<Integer, String> descriptions, ErrorBody body, boolean secured) {
        Map<String, Object> responses = SpecMaps.child(operation, RESPONSES);
        int added = 0;

        for (Map.Entry<Integer, String> entry : descriptions.entrySet()) {
            String status = String.valueOf(entry.getKey());

            if (responses.containsKey(status) || !secured && AUTH_STATUSES.contains(entry.getKey())) {
                continue;
            }

            responses.put(status, response(entry.getValue(), body));
            added++;
        }

        if (added > 0) {
            operation.put(RESPONSES, sorted(responses));
        }

        return added;
    }

    /** operation 에 실제로 요구하는 스키마가 있는가. operation 의 {@code security} 가 문서 최상위 값을 덮는다. */
    static boolean isSecured(Map<String, Object> operation, List<Object> rootSecurity) {
        List<Object> effective = operation.containsKey(SECURITY)
            ? SpecMaps.listOrEmpty(operation.get(SECURITY))
            : rootSecurity;

        return SpecMaps.maps(effective)
            .stream()
            .anyMatch(requirement -> !requirement.isEmpty());
    }

    /**
     * 응답 객체. 호출마다 새 맵을 만든다: 같은 객체를 여러 곳에 넣으면 YAML 출력에 앵커({@code &id001})가 생긴다.
     * 선언한 응답({@link OpenApiDeclaredResponses})도 이것으로 만든다.
     */
    static Map<String, Object> response(String description, ErrorBody body) {
        Map<String, Object> response = new LinkedHashMap<>();

        response.put(DESCRIPTION, description);

        if (body.schema() != null) {
            Map<String, Object> schema = new LinkedHashMap<>();
            Map<String, Object> media = new LinkedHashMap<>();
            Map<String, Object> content = new LinkedHashMap<>();

            schema.put(SpecMaps.REF, SpecMaps.COMPONENT_PREFIX + body.schema());
            media.put(SCHEMA, schema);
            content.put(body.mediaType(), media);
            response.put(CONTENT, content);
        }

        return response;
    }

    /**
     * 본문 스키마. [configured] 가 있으면 {@code components.schemas} 에 있을 때만 쓰고, 없으면 경고를 남기고 설명만 넣는다.
     * [configured] 가 없으면 오류 응답(4xx·5xx)이 직접 가리키는 스키마 중 가장 많이 쓰인 것(같으면 이름순)을 쓴다. 테스트 설정
     * {@code open-api.error-schema} 를 정했으면 그 스키마가 모든 오류 응답에 붙으므로 그것이 고른다.
     * 미디어 타입은 그 스키마를 처음 쓴 오류 응답의 것이다.
     */
    static ErrorBody errorBody(Map<String, Object> document, String configured, List<String> warnings) {
        List<ErrorBody> used = errorBodies(document);

        if (configured != null) {
            if (SpecMaps.map(SpecMaps.componentSchemas(document), configured) == null) {
                warnings.add("openApiCommonResponseSchema '" + configured + "' is not in components.schemas, "
                    + "so common responses have a description only");

                return new ErrorBody(null, null);
            }

            return new ErrorBody(configured, mediaTypeOf(used, configured));
        }

        Map<String, Integer> counts = new TreeMap<>();

        used.forEach(body -> counts.merge(body.schema(), 1, Integer::sum));

        return counts.entrySet()
            .stream()
            .max(Map.Entry.<String, Integer>comparingByValue()
                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
            .map(best -> new ErrorBody(best.getKey(), mediaTypeOf(used, best.getKey())))
            .orElse(new ErrorBody(null, null));
    }

    /** 문서 순서대로 오류 응답이 {@code $ref} 로 가리키는 (스키마, 미디어 타입). */
    private static List<ErrorBody> errorBodies(Map<String, Object> document) {
        List<ErrorBody> bodies = new ArrayList<>();

        for (Map<String, Object> operation : SpecMaps.operations(document)) {
            SpecMaps.mapOrEmpty(operation.get(RESPONSES)).forEach((status, response) -> {
                if (!isError(status)) {
                    return;
                }

                SpecMaps.mapOrEmpty(SpecMaps.map(SpecMaps.map(response), CONTENT)).forEach((mediaType, media) -> {
                    String name = SpecMaps.referencedName(SpecMaps.map(SpecMaps.map(media), SCHEMA));

                    if (name != null) {
                        bodies.add(new ErrorBody(name, mediaType));
                    }
                });
            });
        }

        return bodies;
    }

    private static String mediaTypeOf(List<ErrorBody> bodies, String schema) {
        return bodies.stream()
            .filter(body -> body.schema().equals(schema))
            .map(ErrorBody::mediaType)
            .findFirst()
            .orElse(DEFAULT_MEDIA_TYPE);
    }

    private static boolean isError(String status) {
        if (NUMERIC.matcher(status).matches()) {
            return Integer.parseInt(status) >= ERROR_STATUS;
        }

        return ERROR_RANGE.matcher(status).matches();
    }

    /** 상태 코드 순서로 다시 놓은 응답 맵. */
    static Map<String, Object> sorted(Map<String, Object> responses) {
        Map<String, Object> sorted = new LinkedHashMap<>();

        responses.keySet()
            .stream()
            .sorted(Comparator.comparingLong(OpenApiCommonResponses::rank)
                .thenComparing(Comparator.naturalOrder()))
            .forEach(key -> sorted.put(key, responses.get(key)));

        return sorted;
    }

    /** 숫자 코드는 그 값, {@code 4XX} 는 그 범위의 숫자 코드 뒤, {@code default} 와 그 밖은 맨 뒤. */
    private static long rank(String status) {
        if (NUMERIC.matcher(status).matches()) {
            return Long.parseLong(status) * 2;
        }

        if (RANGE.matcher(status).matches()) {
            return (Character.getNumericValue(status.charAt(0)) * 100L + 99) * 2 + 1;
        }

        return Long.MAX_VALUE;
    }

    /** 상태 코드의 표준 reason phrase. 모르는 코드면 숫자 그대로. */
    static String phrase(int status) {
        String phrase = HttpStatusPhrases.of(status);

        return phrase == null ? String.valueOf(status) : phrase;
    }
}
