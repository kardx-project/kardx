package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUEST_BODY;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUIRED;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.TYPE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.i18n.DocsText;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * epages 가 담지 못해 라이브러리가 {@value OpenApiResources#EXTRAS_FILE} 에 남긴 정보를 스펙에 넣는다.
 * <ul>
 *   <li>multipart 파트 → {@code multipart/form-data} 요청 본문. 파일 파트는 {@code string}/{@code binary}, 본문 필드를 적은
 *       파트는 그 스키마와 {@code encoding.contentType}</li>
 *   <li>요청 쿠키 → {@code in: cookie} 파라미터, 응답 쿠키 → 응답 {@code Set-Cookie} 헤더 설명의 쿠키 목록</li>
 *   <li>필드 없는 본문(값 하나인 JSON, 값 배열)의 스키마 → epages 가 만든 빈 객체 스키마 대신</li>
 * </ul>
 * 같은 operation 의 여러 조각에 있으면 조각 경로 이름순으로 앞선 것을 쓴다.
 */
public final class OpenApiRequestExtras {
    private static final String MULTIPART = "multipart/form-data";
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String NAME = "name";
    private static final String OPTIONAL = "optional";
    private static final String CONTENT_TYPE = "contentType";
    private static final String PARAGRAPH = "\n\n";

    private OpenApiRequestExtras() {
    }

    /** @return 고친 operation 수 */
    static int transform(Map<String, Object> document, List<Resource> resources, DocsLanguage language) {
        Set<String> replaced = new LinkedHashSet<>();
        int changed = 0;

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);

            if (operation == null || resource.extras().isEmpty()) {
                continue;
            }

            boolean touched = multipart(operation, resource.extras());
            touched |= requestCookies(operation, resource.extras());
            touched |= responseCookies(operation, resource, language);
            touched |= bodySchemas(document, operation, resource, replaced);

            if (touched) {
                changed++;
            }
        }

        OpenApiBodySchemas.removeUnreferenced(document, SpecMaps.componentSchemas(document), replaced);

        return changed;
    }

    private static boolean multipart(Map<String, Object> operation, Map<String, Object> extras) {
        List<Map<String, Object>> parts = SpecMaps.maps(extras.get("requestParts"));
        Map<String, Object> existing = map(map(map(operation, REQUEST_BODY), CONTENT), MULTIPART);

        if (parts.isEmpty() || map(map(existing, SCHEMA), "properties") != null) {
            return false;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> encoding = new LinkedHashMap<>();
        List<Object> required = new ArrayList<>();

        for (Map<String, Object> part : parts) {
            String name = String.valueOf(part.get(NAME));
            Map<String, Object> schema = new LinkedHashMap<>();
            Map<String, Object> partSchema = map(part.get(SCHEMA));

            if (partSchema != null) {
                schema.putAll(partSchema);
            }

            Object contentType = part.get(CONTENT_TYPE);

            if (part.get(DESCRIPTION) != null) {
                schema.put(DESCRIPTION, part.get(DESCRIPTION));
            }

            properties.put(name, schema);

            if (!Boolean.TRUE.equals(part.get(OPTIONAL))) {
                required.add(name);
            }

            // 파일이 아니고 본문 필드를 적은 파트는 Content-Type 을 알려 준다 (Swagger UI 가 JSON 으로 보낸다)
            if (contentType != null && !"binary".equals(schema.get("format"))) {
                encoding.put(name, Map.of(CONTENT_TYPE, contentType));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(TYPE, "object");

        if (!required.isEmpty()) {
            body.put(REQUIRED, required);
        }

        body.put("properties", properties);

        Map<String, Object> media = new LinkedHashMap<>();
        media.put(SCHEMA, body);

        if (!encoding.isEmpty()) {
            media.put("encoding", encoding);
        }

        requestBody(operation).put(MULTIPART, media);

        return true;
    }

    /** 요청 본문의 content. 없으면 만들고, 스펙을 읽기 쉽게 {@code responses} 앞에 둔다. */
    private static Map<String, Object> requestBody(Map<String, Object> operation) {
        if (!operation.containsKey(REQUEST_BODY)) {
            Map<String, Object> entries = new LinkedHashMap<>(operation);

            operation.clear();
            entries.forEach((key, value) -> {
                if (key.equals(RESPONSES)) {
                    operation.put(REQUEST_BODY, new LinkedHashMap<>());
                }

                operation.put(key, value);
            });
        }

        return SpecMaps.child(SpecMaps.child(operation, REQUEST_BODY), CONTENT);
    }

    private static boolean requestCookies(Map<String, Object> operation, Map<String, Object> extras) {
        List<Map<String, Object>> cookies = SpecMaps.maps(extras.get("requestCookies"));
        boolean changed = false;

        for (Map<String, Object> cookie : cookies) {
            List<Object> parameters = SpecMaps.childList(operation, SpecMaps.PARAMETERS);
            boolean present = parameters.stream()
                .map(SpecMaps::map)
                .anyMatch(p -> p != null && "cookie".equals(p.get("in")) && cookie.get(NAME).equals(p.get(NAME)));

            if (!present) {
                Map<String, Object> parameter = new LinkedHashMap<>();
                parameter.put(NAME, cookie.get(NAME));
                parameter.put("in", "cookie");
                parameter.put(DESCRIPTION, cookie.get(DESCRIPTION));
                parameter.put(REQUIRED, !Boolean.TRUE.equals(cookie.get(OPTIONAL)));
                parameter.put(SCHEMA, new LinkedHashMap<>(Map.of(TYPE, "string")));
                parameters.add(parameter);
                changed = true;
            }
        }

        return changed;
    }

    /**
     * OpenAPI 3.0 에는 응답 쿠키 자리가 없으므로 {@code Set-Cookie} 헤더 설명에 쿠키 목록({@link DocsText#COOKIES})을 붙인다.
     * 어느 언어로든 목록이 이미 있으면 그대로 둔다.
     */
    private static boolean responseCookies(Map<String, Object> operation, Resource resource, DocsLanguage language) {
        List<Map<String, Object>> cookies = SpecMaps.maps(resource.extras().get("responseCookies"));
        Map<String, Object> response = SpecMaps.response(operation, resource.status());

        if (cookies.isEmpty() || response == null) {
            return false;
        }

        Map<String, Object> headers = SpecMaps.child(response, "headers");
        String key = headers.keySet()
            .stream()
            .filter(SET_COOKIE::equalsIgnoreCase)
            .findFirst()
            .orElse(SET_COOKIE);
        Map<String, Object> header = SpecMaps.child(headers, key);
        String description = SpecMaps.string(header, DESCRIPTION);

        if (description != null && DocsText.COOKIES.variants().stream().anyMatch(description::contains)) {
            return false;
        }

        List<String> lines = new ArrayList<>();

        for (Map<String, Object> cookie : cookies) {
            String optional = Boolean.TRUE.equals(cookie.get(OPTIONAL)) ? " " + DocsText.OPTIONAL.in(language) : "";

            lines.add("`" + cookie.get(NAME) + "`" + optional + ": " + cookie.get(DESCRIPTION));
        }

        String list = DocsText.COOKIES.in(language) + PARAGRAPH + String.join(PARAGRAPH, lines);

        header.put(DESCRIPTION, description == null || description.isBlank() ? list : description + PARAGRAPH + list);
        SpecMaps.child(header, SCHEMA).putIfAbsent(TYPE, "string");

        return true;
    }

    /** 필드가 없어 epages 가 빈 객체 스키마를 만든 본문을 타입에서 만든 스키마로 바꾼다. */
    private static boolean bodySchemas(
        Map<String, Object> document, Map<String, Object> operation, Resource resource, Set<String> replaced) {
        Map<String, Object> request = SpecMaps.requestMedia(
            operation, SpecMaps.string(resource.request(), CONTENT_TYPE));
        Map<String, Object> response = SpecMaps.responseMedia(
            operation, resource.status(), SpecMaps.string(resource.response(), CONTENT_TYPE));

        return replaceEmpty(document, request, map(resource.extras().get(REQUEST_BODY)), replaced)
            | replaceEmpty(document, response, map(resource.extras().get("responseBody")), replaced);
    }

    private static boolean replaceEmpty(
        Map<String, Object> document, Map<String, Object> media, Map<String, Object> schema, Set<String> replaced) {
        if (media == null || schema == null) {
            return false;
        }

        Object current = media.get(SCHEMA);
        Map<String, Object> target = SpecMaps.resolve(document, current);

        if (target == null || !OpenApiBodySchemas.isEmptyObject(target)) {
            return false;
        }

        String name = SpecMaps.referencedName(current);

        if (name != null) {
            replaced.add(name);
        }

        media.put(SCHEMA, new LinkedHashMap<>(schema));

        return true;
    }
}
