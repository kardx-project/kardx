package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.io.JsonFiles;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 같은 메서드·경로의 OpenAPI 조각(resource.json)이 같은 보안 요구사항을 갖게 맞춘다.
 *
 * <p>epages 는 같은 operation 의 조각을 합칠 때 요청 정보를 한 조각 기준으로 잡는다. 그래서 토큰 없이 보낸 테스트
 * (401·404 확인 등)가 섞이면 operation 의 security 와 Swagger UI 의 Authorize 가 사라진다. 하나라도 보안 요구사항이
 * 있으면 없는 조각에 채운다.
 */
public final class SecurityRequirementsAligner {
    private static final String REQUEST = "request";
    private static final String SECURITY_REQUIREMENTS = "securityRequirements";

    private SecurityRequirementsAligner() {
    }

    @SuppressWarnings("unchecked")
    public static int align(File snippetsDirectory) {
        Map<String, List<Path>> byOperation = new HashMap<>();
        Map<Path, Map<String, Object>> resources = new HashMap<>();

        // 경로 순으로 보므로 보안 요구사항을 가져오는 조각이 파일 시스템의 나열 순서와 상관없이 정해진다
        for (Path p : OpenApiResources.files(snippetsDirectory)) {
            Map<String, Object> resource = (Map<String, Object>) JsonFiles.read(p.toFile());
            Map<String, Object> request = (Map<String, Object>) resource.get(REQUEST);

            if (request == null) {
                continue;
            }

            String operation = request.get("method") + " " + request.get("path");

            resources.put(p, resource);
            byOperation.computeIfAbsent(operation, k -> new ArrayList<>())
                .add(p);
        }

        int changed = 0;

        for (List<Path> group : byOperation.values()) {
            Object security = group.stream()
                .map(p -> ((Map<String, Object>) resources.get(p).get(REQUEST)).get(SECURITY_REQUIREMENTS))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

            if (security == null) {
                continue;
            }

            for (Path p : group) {
                Map<String, Object> request = (Map<String, Object>) resources.get(p).get(REQUEST);

                if (request.get(SECURITY_REQUIREMENTS) == null) {
                    request.put(SECURITY_REQUIREMENTS, security);
                    JsonFiles.write(p.toFile(), resources.get(p));
                    changed++;
                }
            }
        }

        return changed;
    }
}
