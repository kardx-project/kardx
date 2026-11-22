package io.github.kardx.tooling.internal.openapi;

import java.util.List;
import java.util.Map;

/**
 * 플러그인 확장 {@code openApiInfo { }} 의 값 ({@link OpenApiInfoMetadata} 가 스펙에 넣는다). 빈 문자열과 null 은 정하지 않은
 * 것으로 본다.
 *
 * @param description {@code info.description} (파일에서 읽은 경우 그 내용). 없으면 null
 * @param termsOfService {@code info.termsOfService}. 없으면 null
 * @param contact {@code info.contact} 의 {@code name}·{@code url}·{@code email}
 * @param license {@code info.license} 의 {@code name}·{@code url}
 * @param externalDocs 최상위 {@code externalDocs} 의 {@code url}·{@code description}
 * @param tagOrder 앞에 둘 태그 이름 순서
 * @param tagDescriptions 태그 이름 → 설명
 * @param tagExternalDocs {@code <태그>.url}·{@code <태그>.description} → 값
 * @param tagGroups {@code x-tagGroups} 그룹 이름 → 태그 이름 (정한 순서)
 */
public record OpenApiInfoSettings(
    String description,
    String termsOfService,
    Map<String, String> contact,
    Map<String, String> license,
    Map<String, String> externalDocs,
    List<String> tagOrder,
    Map<String, String> tagDescriptions,
    Map<String, String> tagExternalDocs,
    Map<String, List<String>> tagGroups
) {
    /** 아무것도 정하지 않은 값. */
    public static final OpenApiInfoSettings NONE =
        new OpenApiInfoSettings(null, null, Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of());
}
