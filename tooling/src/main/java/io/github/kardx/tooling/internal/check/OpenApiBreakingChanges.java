package io.github.kardx.tooling.internal.check;

import static io.github.kardx.tooling.internal.check.SpecNodes.isTrue;
import static io.github.kardx.tooling.internal.check.SpecNodes.resolve;
import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.PATHS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUEST_BODY;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUIRED;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.listOrEmpty;
import static io.github.kardx.tooling.internal.spec.SpecMaps.mapOrEmpty;

import io.github.kardx.tooling.internal.check.SchemaChanges.Direction;
import io.github.kardx.tooling.internal.check.SchemaChanges.Location;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 두 OpenAPI 3 문서 사이에서 기존 클라이언트를 깨뜨리는 변경과 알아 둘 변경(경고)을 찾는다. 판정하는 규칙은 다음뿐이다.
 * <ul>
 *   <li>엔드포인트(경로·메서드) 삭제, 2xx 응답 삭제, 요청·응답 미디어 타입 삭제, 응답 헤더 삭제</li>
 *   <li>필수 파라미터 추가, 파라미터가 필수로 바뀜, 필수 요청 본문 추가, 요청 본문이 필수로 바뀜</li>
 *   <li>요청 본문·파라미터: 필수 필드 추가, 필드가 필수로 바뀜, 제약이 좁아짐 ({@code minLength}·{@code minimum}·
 *       {@code minItems} 증가, {@code maxLength}·{@code maximum}·{@code maxItems} 감소, {@code pattern} 추가·변경)</li>
 *   <li>응답 본문: 필드 삭제, 필수 필드가 선택으로 바뀜</li>
 *   <li>enum: 요청에서 값 삭제와 제한 없던 값에 enum 추가, 응답에서 값 추가 (모르는 값을 받은 클라이언트가 깨진다)</li>
 *   <li>{@code oneOf}: 하위 타입 삭제 (응답에 하위 타입 추가는 경고)</li>
 *   <li>보안: 인증 없던 operation 에 보안 요구사항 추가, 요구하는 보안 스키마 변경</li>
 *   <li>어디서든: 타입·형식 변경({@code type}, {@code format}). {@code default} 변경은 경고</li>
 * </ul>
 * 스키마는 {@code $ref} 를 따라가 비교하므로 스키마 이름만 바뀐 것은 깨는 변경이 아니다. 경로 변수 이름만 바뀐 경로
 * ({@code /users/{id}} → {@code /users/{userId}})도 요청이 같으므로 경고로 알리고, 경로 파라미터는 자리로 짝지어 비교한다.
 * anyOf·allOf·additionalProperties·scope 변경은 판정하지 않는다 (구조 차이에만 나온다).
 */
public final class OpenApiBreakingChanges {
    private static final String HEADERS = "headers";
    private static final String PATH_PARAMETER = "path ";
    /** 경로 변수 ({@code {id}}). */
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]*)}");

    private final Map<?, ?> beforeDocument;
    private final Map<?, ?> afterDocument;
    private final List<String> found = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final SchemaChanges schemas;

    private OpenApiBreakingChanges(Map<?, ?> beforeDocument, Map<?, ?> afterDocument) {
        this.beforeDocument = beforeDocument;
        this.afterDocument = afterDocument;
        this.schemas = new SchemaChanges(beforeDocument, afterDocument, found, warnings);
    }

    /**
     * 판정 결과.
     *
     * @param breaking 깨는 변경 ({@code "Response field removed: GET /users/{id} 200 application/json name"})
     * @param warnings 깨지는 않지만 알아 둘 변경 ({@code "Default changed: …"})
     */
    public record Changes(List<String> breaking, List<String> warnings) {
    }

    /** 깨는 변경 목록 ({@code "Response field removed: GET /users/{id} 200 application/json name"}). */
    public static List<String> find(Map<?, ?> before, Map<?, ?> after) {
        return analyze(before, after).breaking();
    }

    /** 깨는 변경과 경고. */
    public static Changes analyze(Map<?, ?> before, Map<?, ?> after) {
        OpenApiBreakingChanges changes = new OpenApiBreakingChanges(before, after);

        changes.comparePaths(mapOrEmpty(before.get(PATHS)), mapOrEmpty(after.get(PATHS)));

        return new Changes(changes.found, changes.warnings);
    }

    private void comparePaths(Map<?, ?> before, Map<?, ?> after) {
        for (Map.Entry<?, ?> entry : before.entrySet()) {
            String path = String.valueOf(entry.getKey());
            String afterPath = after.containsKey(path) ? path : renamedPath(path, before, after);
            Map<?, ?> beforeItem = mapOrEmpty(entry.getValue());
            Map<?, ?> afterItem = afterPath == null ? Map.of() : mapOrEmpty(after.get(afterPath));
            Map<String, String> renames = afterPath == null ? Map.of() : variableRenames(path, afterPath);

            if (!renames.isEmpty()) {
                warnings.add("Path variables renamed: " + path + " → " + afterPath);
            }

            for (String method : OpenApiSpecFiles.OPERATION_METHODS) {
                if (!beforeItem.containsKey(method)) {
                    continue;
                }

                String operation = method.toUpperCase(Locale.ROOT) + " " + path;

                if (afterItem.containsKey(method)) {
                    compareOperation(operation, beforeItem, afterItem, method, renames);
                } else {
                    found.add("Endpoint removed: " + operation);
                }
            }
        }
    }

    /** 뒤 문서에서 변수 이름만 다른 같은 경로 (앞 문서에 없는 것). 없으면 null. */
    private static String renamedPath(String path, Map<?, ?> before, Map<?, ?> after) {
        String shape = VARIABLE.matcher(path).replaceAll("{}");

        return after.keySet()
            .stream()
            .map(String::valueOf)
            .filter(candidate -> !before.containsKey(candidate))
            .filter(candidate -> VARIABLE.matcher(candidate).replaceAll("{}").equals(shape))
            .findFirst()
            .orElse(null);
    }

    /** 자리가 같은 경로 변수의 이름 변화 (앞 이름 → 뒤 이름). 이름이 같은 변수는 넣지 않는다. */
    private static Map<String, String> variableRenames(String before, String after) {
        List<String> beforeNames = variableNames(before);
        List<String> afterNames = variableNames(after);
        Map<String, String> renames = new LinkedHashMap<>();

        for (int i = 0; i < beforeNames.size() && i < afterNames.size(); i++) {
            if (!beforeNames.get(i).equals(afterNames.get(i))) {
                renames.put(beforeNames.get(i), afterNames.get(i));
            }
        }

        return renames;
    }

    private static List<String> variableNames(String path) {
        List<String> names = new ArrayList<>();
        Matcher matcher = VARIABLE.matcher(path);

        while (matcher.find()) {
            names.add(matcher.group(1));
        }

        return names;
    }

    private void compareOperation(
        String operation, Map<?, ?> beforeItem, Map<?, ?> afterItem, String method, Map<String, String> renames) {
        Map<?, ?> before = mapOrEmpty(beforeItem.get(method));
        Map<?, ?> after = mapOrEmpty(afterItem.get(method));

        String security = SecurityChanges.compare(operation, beforeDocument, before, afterDocument, after);

        if (security != null) {
            found.add(security);
        }

        compareParameters(operation, renamed(parameters(beforeDocument, beforeItem, before), renames),
            parameters(afterDocument, afterItem, after));
        compareRequestBody(operation, mapOrEmpty(resolve(beforeDocument, before.get(REQUEST_BODY))),
            mapOrEmpty(resolve(afterDocument, after.get(REQUEST_BODY))), before.containsKey(REQUEST_BODY));
        compareResponses(operation, mapOrEmpty(before.get(RESPONSES)), mapOrEmpty(after.get(RESPONSES)));
    }

    private void compareParameters(String operation, Map<String, Map<?, ?>> before, Map<String, Map<?, ?>> after) {
        for (Map.Entry<String, Map<?, ?>> entry : after.entrySet()) {
            Map<?, ?> previous = before.get(entry.getKey());
            boolean required = isTrue(entry.getValue().get(REQUIRED));
            String location = operation + " parameter " + entry.getKey();

            if (previous == null) {
                if (required) {
                    found.add("Required parameter added: " + location);
                }

                continue;
            }

            if (required && !isTrue(previous.get(REQUIRED))) {
                found.add("Parameter became required: " + location);
            }

            schemas.compareSchema(Direction.REQUEST, new Location(location, ""), previous.get(SCHEMA),
                entry.getValue().get(SCHEMA), 0);
        }
    }

    private void compareRequestBody(String operation, Map<?, ?> before, Map<?, ?> after, boolean existed) {
        boolean required = isTrue(after.get(REQUIRED));

        if (!existed) {
            if (required) {
                found.add("Required request body added: " + operation);
            }

            return;
        }

        if (required && !isTrue(before.get(REQUIRED))) {
            found.add("Request body became required: " + operation);
        }

        compareContent(Direction.REQUEST, operation + " request", mapOrEmpty(before.get(CONTENT)),
            mapOrEmpty(after.get(CONTENT)));
    }

    private void compareResponses(String operation, Map<?, ?> before, Map<?, ?> after) {
        for (Map.Entry<?, ?> entry : before.entrySet()) {
            String status = String.valueOf(entry.getKey());
            String location = operation + " " + status;

            if (!after.containsKey(entry.getKey())) {
                if (status.startsWith("2")) {
                    found.add("Success response removed: " + location);
                }

                continue;
            }

            Map<?, ?> beforeResponse = mapOrEmpty(resolve(beforeDocument, entry.getValue()));
            Map<?, ?> afterResponse = mapOrEmpty(resolve(afterDocument, after.get(entry.getKey())));

            compareHeaders(location, mapOrEmpty(beforeResponse.get(HEADERS)), mapOrEmpty(afterResponse.get(HEADERS)));
            compareContent(Direction.RESPONSE, location, mapOrEmpty(beforeResponse.get(CONTENT)),
                mapOrEmpty(afterResponse.get(CONTENT)));
        }
    }

    /** 클라이언트가 읽던 응답 헤더가 빠지면 깨진다. 헤더 이름은 대소문자를 가리지 않는다. */
    private void compareHeaders(String location, Map<?, ?> before, Map<?, ?> after) {
        Set<String> remaining = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        after.keySet().forEach(name -> remaining.add(String.valueOf(name)));
        before.keySet()
            .stream()
            .map(String::valueOf)
            .filter(name -> !remaining.contains(name))
            .forEach(name -> found.add("Response header removed: " + location + " " + name));
    }

    private void compareContent(Direction direction, String location, Map<?, ?> before, Map<?, ?> after) {
        for (Map.Entry<?, ?> entry : before.entrySet()) {
            String media = location + " " + entry.getKey();

            if (!after.containsKey(entry.getKey())) {
                found.add((direction == Direction.REQUEST ? "Request" : "Response") + " media type removed: " + media);
                continue;
            }

            schemas.compareSchema(direction, new Location(media, ""), mapOrEmpty(entry.getValue()).get(SCHEMA),
                mapOrEmpty(after.get(entry.getKey())).get(SCHEMA), 0);
        }
    }

    /** 이름이 바뀐 경로 변수의 파라미터를 뒤 이름으로 찾게 한다 (보고 문구의 파라미터 이름도 뒤 이름이다). */
    private static Map<String, Map<?, ?>> renamed(Map<String, Map<?, ?>> parameters, Map<String, String> renames) {
        if (renames.isEmpty()) {
            return parameters;
        }

        Map<String, Map<?, ?>> keyed = new LinkedHashMap<>();

        parameters.forEach((key, parameter) -> {
            String name = key.startsWith(PATH_PARAMETER) ? renames.get(key.substring(PATH_PARAMETER.length())) : null;

            keyed.put(name == null ? key : PATH_PARAMETER + name, parameter);
        });

        return keyed;
    }

    /** 경로·operation 파라미터 ({@code in name} → 파라미터). operation 쪽이 같은 이름의 경로 파라미터를 덮는다. */
    private static Map<String, Map<?, ?>> parameters(Map<?, ?> document, Map<?, ?> pathItem, Map<?, ?> operation) {
        Map<String, Map<?, ?>> parameters = new LinkedHashMap<>();

        List<List<Object>> sources =
            List.of(listOrEmpty(pathItem.get("parameters")), listOrEmpty(operation.get("parameters")));

        for (List<Object> source : sources) {
            for (Object node : source) {
                Map<?, ?> parameter = mapOrEmpty(resolve(document, node));

                parameters.put(parameter.get("in") + " " + parameter.get("name"), parameter);
            }
        }

        return parameters;
    }
}
