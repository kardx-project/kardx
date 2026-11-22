package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.List;
import java.util.Map;

/**
 * Kotlin 생성자 파라미터의 기본값을 본문 필드 스키마의 {@code default} 로 넣는다. 라이브러리가
 * {@value OpenApiResources#EXTRAS_FILE} 의 {@code fieldDefaults} ({@code requestBody}·{@code responseBody}: REST Docs 필드
 * 경로 → 값)에 남긴다. 공유 스키마({@code $ref})를 가리키는 필드는 건드리지 않는다. 디스크립터의
 * {@code withDefaultValue} 로 이미 {@code default} 가 있으면 그것이 이긴다.
 * 다형 본문을 {@code oneOf} 로 바꾸기({@link OpenApiPolymorphism}) 전에 실행한다 (필드 경로로 찾으므로).
 */
final class OpenApiFieldDefaults {
    private static final String DEFAULT = "default";
    private static final String CONTENT_TYPE = "contentType";

    private OpenApiFieldDefaults() {
    }

    /** @return {@code default} 를 넣은 필드 수 */
    static int transform(Map<String, Object> document, List<Resource> resources) {
        int changed = 0;

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);

            if (operation == null) {
                continue;
            }

            changed += apply(document, SpecMaps.requestMedia(operation,
                SpecMaps.string(resource.request(), CONTENT_TYPE)), resource.fieldDefaults("requestBody"));
            changed += apply(document, SpecMaps.responseMedia(operation, resource.status(),
                SpecMaps.string(resource.response(), CONTENT_TYPE)), resource.fieldDefaults("responseBody"));
        }

        return changed;
    }

    private static int apply(Map<String, Object> document, Map<String, Object> media, Map<String, Object> defaults) {
        int changed = 0;

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            SchemaSlot slot = entry.getKey().isEmpty() ? null : SchemaSlot.locate(document, media, entry.getKey());
            // 공유 스키마($ref)는 다른 필드도 가리키므로 고치지 않는다
            Map<String, Object> schema = slot == null || SpecMaps.referencedName(slot.schema()) != null
                ? null
                : SpecMaps.map(slot.schema());

            if (schema != null && entry.getValue() != null && !schema.containsKey(DEFAULT)) {
                schema.put(DEFAULT, entry.getValue());
                changed++;
            }
        }

        return changed;
    }
}
