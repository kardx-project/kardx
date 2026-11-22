package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.CONTENT;
import static io.github.kardx.tooling.internal.spec.SpecMaps.REQUEST_BODY;
import static io.github.kardx.tooling.internal.spec.SpecMaps.RESPONSES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SCHEMA;
import static io.github.kardx.tooling.internal.spec.SpecMaps.TYPE;
import static io.github.kardx.tooling.internal.spec.SpecMaps.map;

import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JSON 이 아닌 본문(파일 다운로드, {@code text/plain})의 스키마를 문자열로 바꾼다.
 *
 * <p>epages 는 본문 스키마를 문서화한 필드로 만들므로, 필드가 없는 본문은 모두 빈 {@code type: object} 스키마가 되고
 * 첫 operation 의 경로로 이름이 붙는다({@code data-page-33084672}). 미디어 타입이 JSON 이 아니면 그 스키마를
 * {@code type: string} 으로 바꾸고, {@code text/*} 가 아니면 {@code format: binary} 를 붙이고 예시({@code examples})를 지운다
 * (라이브러리가 바이너리 본문을 문서에 {@code <binary: N bytes>} 로만 남기므로 예시로 쓸 값이 없다). 아무도 가리키지 않게 된
 * 빈 스키마는 지운다. JSON 본문은 건드리지 않는다.
 */
public final class OpenApiBodySchemas {
    private OpenApiBodySchemas() {
    }

    /** @return 바꾼 본문 수 */
    public static int apply(File specDirectory) {
        return OpenApiSpecFiles.rewriteOpenApi3(specDirectory, OpenApiBodySchemas::transform);
    }

    /** 문서 트리를 고친다. @return 바꾼 본문 수 */
    static int transform(Map<String, Object> document) {
        Map<String, Object> schemas = SpecMaps.componentSchemas(document);
        Set<String> replaced = new LinkedHashSet<>();
        int changed = 0;

        for (Map<String, Object> operation : SpecMaps.operations(document)) {
            changed += replaceOperation(operation, schemas, replaced);
        }

        removeUnreferenced(document, schemas, replaced);

        return changed;
    }

    /** operation 의 요청 본문과 모든 응답 본문을 고친다. @return 바꾼 본문 수 */
    private static int replaceOperation(
        Map<String, Object> operation, Map<String, Object> schemas, Set<String> replaced) {
        int changed = replace(map(map(operation, REQUEST_BODY), CONTENT), schemas, replaced);
        Map<String, Object> responses = map(operation, RESPONSES);

        if (responses != null) {
            for (Object response : responses.values()) {
                changed += replace(map(map(response), CONTENT), schemas, replaced);
            }
        }

        return changed;
    }

    private static int replace(Map<String, Object> content, Map<String, Object> schemas, Set<String> replaced) {
        if (content == null) {
            return 0;
        }

        int changed = 0;

        for (Map.Entry<String, Object> entry : content.entrySet()) {
            String mediaType = entry.getKey().toLowerCase(Locale.ROOT);
            Map<String, Object> media = map(entry.getValue());

            if (media == null || mediaType.contains("json")) {
                continue;
            }

            Object schema = media.get(SCHEMA);
            String referenced = SpecMaps.referencedName(schema);
            Map<String, Object> target = referenced == null ? map(schema) : map(schemas, referenced);

            if (target == null || !isEmptyObject(target)) {
                continue;
            }

            Map<String, Object> string = new LinkedHashMap<>();
            string.put(TYPE, "string");

            if (!mediaType.startsWith("text/")) {
                string.put("format", "binary");
                media.remove("examples");
                media.remove("example");
            }

            media.put(SCHEMA, string);

            if (referenced != null) {
                replaced.add(referenced);
            }

            changed++;
        }

        return changed;
    }

    /** 바꾼 빈 스키마 중 문서 어디에서도 가리키지 않는 것을 지운다. */
    static void removeUnreferenced(
        Map<String, Object> document, Map<String, Object> schemas, Set<String> names) {
        if (schemas == null || names.isEmpty()) {
            return;
        }

        Set<String> referenced = SpecMaps.referencedSchemaNames(document);

        for (String name : names) {
            if (!referenced.contains(name)) {
                schemas.remove(name);
            }
        }
    }

    /** 속성·items·조합이 없는 객체 스키마 (epages 가 필드 없는 본문에 만드는 모양). */
    static boolean isEmptyObject(Map<String, Object> schema) {
        for (String key : schema.keySet()) {
            if (!key.equals(TYPE) && !key.equals("title")) {
                return false;
            }
        }

        return "object".equals(schema.get(TYPE)) || !schema.containsKey(TYPE);
    }
}
