package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 테스트가 선언한 OAuth2·OpenID Connect 요구사항과 확장의 보안 스키마 ({@link OpenApiOAuthSecurity}). */
class OpenApiOAuthSecurityTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /users:
            get:
              operationId: listUsers
              security: [{bearerAuthJWT: [], X-Request-Id: []}]
              responses: {"200": {description: OK}}
          /me:
            get:
              operationId: me
              responses: {"200": {description: OK}}
        components:
          securitySchemes:
            bearerAuthJWT: {type: http, scheme: bearer, bearerFormat: JWT}
            X-Request-Id: {type: apiKey, in: header, name: X-Request-Id}
        """;
    private static final String OAUTH2_USERS = """
        { "security": [ { "type": "oauth2", "scopes": ["users:read"] } ] }
        """;

    @TempDir
    File snippets;

    @Test
    void replacesBearerWithDeclaredSchemesAndBuildsComponents() throws IOException {
        SnippetFixtures.fragment(snippets, "list-users", "GET", "/users", 200, OAUTH2_USERS);
        SnippetFixtures.fragment(snippets, "list-users-admin", "GET", "/users", 200, """
            { "security": [ { "type": "oauth2", "scopes": ["users:admin"] } ] }
            """);
        SnippetFixtures.fragment(snippets, "me", "GET", "/me", 200, """
            { "security": [ { "type": "openIdConnect", "scopes": ["openid"] } ] }
            """);
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        Map<String, String> flows = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        flows.put("clientCredentials.tokenUrl", "https://auth.example.com/oauth/token");
        flows.put("authorizationCode.authorizationUrl", "https://auth.example.com/authorize");
        flows.put("authorizationCode.tokenUrl", "https://auth.example.com/oauth/token");

        OpenApiSecuritySettings settings = new OpenApiSecuritySettings(flows, Map.of("users:read", "Read users"),
            "https://auth.example.com/.well-known/openid-configuration");

        assertEquals(2, OpenApiOAuthSecurity.transform(document, OpenApiResources.read(snippets), settings,
            warnings));

        Map<String, Object> schemes = SpecMaps.map(SpecMaps.map(document, "components"), "securitySchemes");
        Map<String, Object> scopes = Map.of("users:read", "Read users", "users:admin", "");

        assertEquals(List.of(), warnings);
        assertEquals(List.of(Map.of("oauth2", List.of("users:admin", "users:read"), "X-Request-Id", List.of())),
            SpecMaps.operation(document, "/users", "get").get("security"),
            "Bearer 대신 oauth2, 전역 헤더 요구사항은 그대로, 조각들의 scope 를 합친다");
        assertEquals(List.of(Map.of("openIdConnect", List.of("openid"))),
            SpecMaps.operation(document, "/me", "get").get("security"));
        assertEquals(Map.of("type", "oauth2", "flows", Map.of(
                "clientCredentials", Map.of("tokenUrl", "https://auth.example.com/oauth/token", "scopes", scopes),
                "authorizationCode", Map.of("authorizationUrl", "https://auth.example.com/authorize",
                    "tokenUrl", "https://auth.example.com/oauth/token", "scopes", scopes))),
            schemes.get("oauth2"), "쓴 scope 중 정하지 않은 것은 빈 설명으로 더한다");
        assertEquals(Map.of("type", "openIdConnect",
                "openIdConnectUrl", "https://auth.example.com/.well-known/openid-configuration"),
            schemes.get("openIdConnect"));
    }

    @Test
    void unconfiguredSchemeDropsRequirementWithWarning() throws IOException {
        SnippetFixtures.fragment(snippets, "list-users", "GET", "/users", 200, OAUTH2_USERS);
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        List<String> warnings = new ArrayList<>();

        assertEquals(0, OpenApiOAuthSecurity.transform(document, OpenApiResources.read(snippets),
            OpenApiSecuritySettings.NONE, warnings));

        assertEquals(List.of(Map.of("bearerAuthJWT", List.of(), "X-Request-Id", List.of())),
            SpecMaps.operation(document, "/users", "get").get("security"), "요구사항을 바꾸지 않는다");
        assertNull(SpecMaps.map(SpecMaps.map(document, "components"), "securitySchemes").get("oauth2"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("operation 'listUsers' declares oauth2 [users:read] but "
            + "kardx.openApiSecurity does not configure it"), warnings.get(0));
        assertTrue(warnings.get(0).contains("oauth2 { clientCredentials("), warnings.get(0));
    }
}
