package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.DESCRIPTION;
import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUEST_BODY;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.TYPE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파일 본문(PDF 다운로드, {@code application/octet-stream} 업로드 …)을 다듬는다.
 * <ul>
 *   <li>라이브러리가 {@value OpenApiResources#EXTRAS_FILE} 에 남긴 {@code requestFile}·{@code responseFile} 설명 → 본문 스키마의
 *       {@code description}. epages 가 만든 빈 객체 스키마가 남아 있으면(JSON 미디어 타입) {@code type: string} 으로 바꾼다</li>
 *   <li>파일 응답에 문서화한 {@code Content-Disposition} 헤더 → 실제 응답 값을 {@code example} 로</li>
 *   <li>바이너리 본문({@code format: binary}, {@link OpenApiBodySchemas})의 미디어 타입 키에서 {@code charset} 파라미터를 뺀다
 *       ({@code application/pdf;charset=UTF-8} → {@code application/pdf}). 글자가 아닌 본문에는 문자 집합이 없다</li>
 * </ul>
 * 미디어 타입은 조각의 Content-Type(charset 포함)으로 찾으므로 charset 정리는 이 단계의 마지막에 한다.
 */
final class OpenApiFileBodies {
    private static final String BINARY = "binary";
    private static final String FORMAT = "format";
    private static final String HEADERS = "headers";
    private static final String NAME = "name";
    private static final String CONTENT_TYPE = "contentType";
    private static final String CONTENT_DISPOSITION = "Content-Disposition";
    private static final String CHARSET = "charset=";

    private OpenApiFileBodies() {
    }

    /** @return 설명·예시를 넣거나 미디어 타입 키를 고친 operation 수 */
    static int transform(Map<String, Object> document, List<Resource> resources) {
        Set<String> replaced = new LinkedHashSet<>();
        Set<Map<String, Object>> changed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);

            if (operation != null && describe(document, operation, resource, replaced)) {
                changed.add(operation);
            }
        }

        OpenApiBodySchemas.removeUnreferenced(document, SpecMaps.componentSchemas(document), replaced);

        for (Map<String, Object> operation : SpecMaps.operations(document)) {
            if (withoutCharset(operation)) {
                changed.add(operation);
            }
        }

        return changed.size();
    }

    private static boolean describe(
        Map<String, Object> document, Map<String, Object> operation, Resource resource, Set<String> replaced) {
        Map<String, Object> request = SpecMaps.requestMedia(
            operation, SpecMaps.string(resource.request(), CONTENT_TYPE));
        Map<String, Object> response = SpecMaps.responseMedia(
            operation, resource.status(), SpecMaps.string(resource.response(), CONTENT_TYPE));
        Object responseFile = resource.extras().get("responseFile");
        boolean touched = describeBody(document, request, resource.extras().get("requestFile"), replaced);

        touched |= describeBody(document, response, responseFile, replaced);

        if (responseFile != null || isBinary(response)) {
            touched |= dispositionExample(operation, resource);
        }

        return touched;
    }

    /** 본문 스키마에 [file] 의 설명을 넣는다. 다른 operation 과 같이 쓰는 컴포넌트 스키마는 건드리지 않는다. */
    private static boolean describeBody(
        Map<String, Object> document, Map<String, Object> media, Object file, Set<String> replaced) {
        String description = SpecMaps.string(map(file), DESCRIPTION);

        if (media == null || description == null) {
            return false;
        }

        Object current = media.get(SCHEMA);
        String referenced = SpecMaps.referencedName(current);
        Map<String, Object> target = SpecMaps.resolve(document, current);
        Map<String, Object> schema = map(current);

        if (target != null && OpenApiBodySchemas.isEmptyObject(target)) {
            if (referenced != null) {
                replaced.add(referenced);
            }

            schema = new LinkedHashMap<>();
            schema.put(TYPE, "string");
            media.put(SCHEMA, schema);
        } else if (referenced != null || schema == null) {
            return false;
        }

        schema.put(DESCRIPTION, description);

        return true;
    }

    /** 응답 {@code Content-Disposition} 헤더에 실제 응답 값을 예시로 넣는다. 이미 예시가 있으면 그대로 둔다. */
    private static boolean dispositionExample(Map<String, Object> operation, Resource resource) {
        Map<String, Object> headers = map(SpecMaps.response(operation, resource.status()), HEADERS);
        Object value = resource.responseDescriptors(HEADERS)
            .stream()
            .filter(descriptor -> CONTENT_DISPOSITION.equalsIgnoreCase(String.valueOf(descriptor.get(NAME))))
            .map(descriptor -> descriptor.get(EXAMPLE))
            .findFirst()
            .orElse(null);

        if (headers == null || value == null) {
            return false;
        }

        Map<String, Object> header = headers.entrySet()
            .stream()
            .filter(entry -> CONTENT_DISPOSITION.equalsIgnoreCase(entry.getKey()))
            .map(entry -> map(entry.getValue()))
            .findFirst()
            .orElse(null);

        if (header == null || header.containsKey(EXAMPLE)) {
            return false;
        }

        header.put(EXAMPLE, value);

        return true;
    }

    /** operation 의 요청·응답 본문에서 바이너리 미디어 타입 키의 charset 을 뺀다. */
    private static boolean withoutCharset(Map<String, Object> operation) {
        boolean changed = withoutCharsetKeys(map(map(operation, REQUEST_BODY), CONTENT));
        Map<String, Object> responses = map(operation, RESPONSES);

        if (responses != null) {
            for (Object response : responses.values()) {
                changed |= withoutCharsetKeys(map(map(response), CONTENT));
            }
        }

        return changed;
    }

    /** 키 순서는 그대로 두고 이름만 바꾼다. 바꾼 이름이 이미 있으면 앞의 것을 남긴다. */
    private static boolean withoutCharsetKeys(Map<String, Object> content) {
        if (content == null) {
            return false;
        }

        Map<String, Object> renamed = new LinkedHashMap<>();
        boolean changed = false;

        for (Map.Entry<String, Object> entry : content.entrySet()) {
            String key = isBinary(map(entry.getValue())) ? withoutCharset(entry.getKey()) : entry.getKey();

            changed |= !key.equals(entry.getKey());
            renamed.putIfAbsent(key, entry.getValue());
        }

        if (changed) {
            content.clear();
            content.putAll(renamed);
        }

        return changed;
    }

    private static boolean isBinary(Map<String, Object> media) {
        return BINARY.equals(SpecMaps.string(map(media, SCHEMA), FORMAT));
    }

    private static String withoutCharset(String mediaType) {
        return Arrays.stream(mediaType.split(";"))
            .map(String::trim)
            .filter(parameter -> !parameter.toLowerCase(Locale.ROOT).startsWith(CHARSET))
            .collect(Collectors.joining(";"));
    }
}
