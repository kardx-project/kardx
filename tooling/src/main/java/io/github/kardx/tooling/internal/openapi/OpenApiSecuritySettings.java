package io.github.kardx.tooling.internal.openapi;

import java.util.Map;

/**
 * 플러그인 확장 {@code openApiSecurity { }} 의 값. 테스트가 선언한 OAuth2·OpenID Connect 요구사항이 가리킬 보안 스키마를 만든다
 * ({@link OpenApiOAuthSecurity}).
 *
 * @param flows OAuth2 flow 주소 ({@code clientCredentials.tokenUrl} → 주소). 비면 {@code oauth2} 스키마를 만들 수 없다
 * @param scopes OAuth2 scope 이름 → 설명
 * @param openIdConnectUrl OpenID Connect 설정 문서 주소. null 이면 {@code openIdConnect} 스키마를 만들 수 없다
 */
public record OpenApiSecuritySettings(Map<String, String> flows, Map<String, String> scopes, String openIdConnectUrl) {
    /** 아무것도 정하지 않은 값. */
    public static final OpenApiSecuritySettings NONE = new OpenApiSecuritySettings(Map.of(), Map.of(), null);
}
