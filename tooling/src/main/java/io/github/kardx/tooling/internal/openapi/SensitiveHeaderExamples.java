package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.EXAMPLE;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code Authorization}·{@code Cookie} 같은 민감 헤더 파라미터에서 epages 가 요청 값으로 넣은 예시(마스킹 값, 보안 스키마용 가짜
 * JWT, 테스트 토큰)를 뺀다. 사용자가 {@code example}·{@code withDefaultValue} 로 직접 지정한 값은 이 뒤에
 * {@link OpenApiSchemaHints} 가 넣으므로 그대로 남는다.
 */
final class SensitiveHeaderExamples {
    private static final Set<String> SENSITIVE_HEADERS =
        Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");

    private SensitiveHeaderExamples() {
    }

    static void remove(Map<String, Object> document) {
        SpecMaps.operations(document)
            .stream()
            .flatMap(operation -> SpecMaps.maps(operation.get(SpecMaps.PARAMETERS)).stream())
            .filter(p -> "header".equals(p.get("in")))
            .filter(p -> SENSITIVE_HEADERS.contains(String.valueOf(p.get("name")).toLowerCase(Locale.ROOT)))
            .forEach(p -> p.remove(EXAMPLE));
    }
}
