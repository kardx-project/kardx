package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.COMPONENTS;
import static io.github.kardx.tooling.internal.spec.SpecMaps.SECURITY_SCHEMES;
import static io.github.kardx.tooling.internal.spec.SpecMaps.TYPE;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 테스트가 {@code openApi { oauth2("users:read") }}·{@code openIdConnect(...)} 로 선언한 보안 요구사항
 * ({@value OpenApiResources#EXTRAS_FILE} 의 {@code security})을 operation 에 넣고, 플러그인 확장
 * {@code openApiSecurity { }} 로 보안 스키마({@value #OAUTH2}, {@value #OPEN_ID_CONNECT})를 만든다.
 * <ul>
 *   <li>OAuth2 토큰은 Bearer 로 보내므로 epages 가 실제 요청에서 만든 {@code http}/{@code bearer} 요구사항을 대신한다.
 *       전역 헤더({@code apiKey}) 같은 다른 요구사항은 그대로 함께 요구한다</li>
 *   <li>같은 operation 의 여러 조각이 선언했으면 종류별로 scope 를 합친다</li>
 *   <li>확장으로 정하지 않은 스키마는 유효한 OpenAPI 를 만들 수 없으므로(flow·주소가 필수) 요구사항을 빼고 경고한다</li>
 *   <li>{@value #OAUTH2} flow 마다 정한 scope 와 operation 이 쓴 scope 를 모두 적는다 (쓴 scope 가 정의되지 않은 스펙이 되지 않게)</li>
 * </ul>
 */
final class OpenApiOAuthSecurity {
    static final String OAUTH2 = "oauth2";
    static final String OPEN_ID_CONNECT = "openIdConnect";
    private static final String SECURITY = "security";
    private static final String SCOPES = "scopes";
    private static final String SEPARATOR = ".";

    private OpenApiOAuthSecurity() {
    }

    /** 한 operation 과 그 operation 의 조각들이 선언한 종류 → scope. */
    private record Declared(Map<String, Object> operation, Map<String, Set<String>> byType) {
    }

    /**
     * 문서 트리를 고친다.
     *
     * @param warnings 정하지 않은 스키마를 쓴 operation 을 여기에 더한다
     * @return 요구사항을 넣은 operation 수
     */
    static int transform(Map<String, Object> document, List<Resource> resources, OpenApiSecuritySettings settings,
        List<String> warnings) {
        Set<String> usedScopes = new TreeSet<>();
        int changed = 0;

        for (Declared declared : declaredByOperation(document, resources)) {
            Map<String, Set<String>> usable = usable(declared.operation(), declared.byType(), settings, warnings);

            usedScopes.addAll(usable.getOrDefault(OAUTH2, Set.of()));

            if (!usable.isEmpty()) {
                declared.operation().put(SECURITY, requirements(document, declared.operation(), usable));
                changed++;
            }
        }

        addSchemes(document, settings, usedScopes);

        return changed;
    }

    /** operation 마다 선언한 종류 → scope. 조각 경로 이름순으로 모은다. */
    private static Collection<Declared> declaredByOperation(Map<String, Object> document, List<Resource> resources) {
        Map<String, Declared> declared = new LinkedHashMap<>();

        for (Resource resource : resources) {
            Map<String, Object> operation = resource.operation(document);

            if (operation == null || resource.security().isEmpty()) {
                continue;
            }

            Map<String, Set<String>> byType = declared.computeIfAbsent(resource.method() + " " + resource.path(),
                k -> new Declared(operation, new LinkedHashMap<>())).byType();

            for (Map<String, Object> requirement : resource.security()) {
                String type = SpecMaps.string(requirement, TYPE);

                if (OAUTH2.equals(type) || OPEN_ID_CONNECT.equals(type)) {
                    Set<String> scopes = byType.computeIfAbsent(type, k -> new TreeSet<>());

                    SpecMaps.listOrEmpty(requirement.get(SCOPES)).forEach(scope -> scopes.add(String.valueOf(scope)));
                }
            }
        }

        return declared.values();
    }

    /** 스키마를 만들 수 있는 요구사항만. 나머지는 경고한다. */
    private static Map<String, Set<String>> usable(Map<String, Object> operation, Map<String, Set<String>> declared,
        OpenApiSecuritySettings settings, List<String> warnings) {
        Map<String, Set<String>> usable = new LinkedHashMap<>();

        declared.forEach((type, scopes) -> {
            if (configured(type, settings)) {
                usable.put(type, scopes);
            } else {
                warnings.add(missingScheme(operation, type, scopes));
            }
        });

        return usable;
    }

    private static boolean configured(String type, OpenApiSecuritySettings settings) {
        if (OAUTH2.equals(type)) {
            return !settings.flows().isEmpty();
        }

        return settings.openIdConnectUrl() != null && !settings.openIdConnectUrl().isBlank();
    }

    private static String missingScheme(Map<String, Object> operation, String type, Set<String> scopes) {
        String example = OAUTH2.equals(type)
            ? "oauth2 { clientCredentials(\"https://auth.example.com/oauth/token\") }"
            : "openIdConnect(\"https://auth.example.com/.well-known/openid-configuration\")";
        String name = SpecMaps.string(operation, "operationId");

        return (name == null ? "an operation" : "operation '" + name + "'") + " declares " + type + " " + scopes
            + " but kardx.openApiSecurity does not configure it, so the security requirement is dropped. "
            + "Configure it: kardx { openApiSecurity { " + example + " } }";
    }

    /**
     * 새 요구사항 목록. 지금 요구사항(없으면 문서 최상위)에서 Bearer 스키마를 뺀 나머지를 바탕으로, 선언한 종류마다
     * 대안 요구사항 하나를 만든다.
     */
    private static List<Object> requirements(
        Map<String, Object> document, Map<String, Object> operation, Map<String, Set<String>> usable) {
        Object current = operation.containsKey(SECURITY) ? operation.get(SECURITY) : document.get(SECURITY);
        Map<String, Object> base = new LinkedHashMap<>();
        Set<String> bearer = bearerSchemes(document);

        SpecMaps.maps(current).forEach(requirement -> requirement.forEach((scheme, scopes) -> {
            if (!bearer.contains(scheme)) {
                base.putIfAbsent(scheme, scopes);
            }
        }));

        List<Object> requirements = new ArrayList<>();

        usable.forEach((type, scopes) -> {
            Map<String, Object> requirement = new LinkedHashMap<>();

            requirement.put(type, new ArrayList<>(scopes));
            base.forEach((scheme, value) -> requirement.put(scheme, SpecMaps.deepCopy(value)));
            requirements.add(requirement);
        });

        return requirements;
    }

    private static Set<String> bearerSchemes(Map<String, Object> document) {
        Set<String> names = new TreeSet<>();

        SpecMaps.mapOrEmpty(SpecMaps.map(SpecMaps.map(document, COMPONENTS), SECURITY_SCHEMES))
            .forEach((name, scheme) -> {
                Map<String, Object> map = SpecMaps.map(scheme);

                if (map != null && "http".equals(map.get(TYPE))
                    && "bearer".equalsIgnoreCase(String.valueOf(map.get("scheme")))) {
                    names.add(name);
                }
            });

        return names;
    }

    /** 확장으로 정한 스키마를 {@code components.securitySchemes} 에 넣는다. 이미 있으면 그대로 둔다. */
    private static void addSchemes(Map<String, Object> document, OpenApiSecuritySettings settings,
        Set<String> usedScopes) {
        boolean oauth2 = configured(OAUTH2, settings);
        boolean openIdConnect = configured(OPEN_ID_CONNECT, settings);

        if (!oauth2 && !openIdConnect) {
            return;
        }

        Map<String, Object> schemes = SpecMaps.child(SpecMaps.child(document, COMPONENTS), SECURITY_SCHEMES);

        if (oauth2) {
            schemes.putIfAbsent(OAUTH2, oauth2Scheme(settings, usedScopes));
        }

        if (openIdConnect) {
            Map<String, Object> scheme = new LinkedHashMap<>();

            scheme.put(TYPE, OPEN_ID_CONNECT);
            scheme.put("openIdConnectUrl", settings.openIdConnectUrl());
            schemes.putIfAbsent(OPEN_ID_CONNECT, scheme);
        }
    }

    private static Map<String, Object> oauth2Scheme(OpenApiSecuritySettings settings, Set<String> usedScopes) {
        Map<String, Object> scopes = new LinkedHashMap<>(settings.scopes());
        Map<String, Object> flows = new LinkedHashMap<>();

        usedScopes.forEach(scope -> scopes.putIfAbsent(scope, ""));
        settings.flows().forEach((key, url) -> {
            int dot = key.indexOf(SEPARATOR);

            if (dot > 0) {
                SpecMaps.child(flows, key.substring(0, dot)).put(key.substring(dot + 1), url);
            }
        });
        // 주소 다음에 scope 를 둔다. flow 마다 새 맵이어야 YAML 출력에 앵커가 생기지 않는다
        flows.values().forEach(flow -> SpecMaps.mapOrEmpty(flow).put(SCOPES, new LinkedHashMap<>(scopes)));

        Map<String, Object> scheme = new LinkedHashMap<>();

        scheme.put(TYPE, OAUTH2);
        scheme.put("flows", flows);

        return scheme;
    }
}
