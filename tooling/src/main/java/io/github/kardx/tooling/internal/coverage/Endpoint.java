package io.github.kardx.tooling.internal.coverage;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 앱이 가진 엔드포인트 하나 (문서화 테스트가 Spring 컨텍스트에서 읽어 남긴 목록의 원소).
 *
 * @param method HTTP 메서드 (대문자). 매핑에 메서드가 없으면 {@value #ANY_METHOD}
 * @param path 경로 패턴 ({@code /api/v1/users/{id}})
 * @param handler 핸들러 ({@code com.example.UserController#getUser}). 모르면 null
 * @param deprecated 핸들러가 {@code @Deprecated} 인지
 * @param apiVersion 핸들러의 API 버전. 없으면 null
 */
public record Endpoint(String method, String path, String handler, boolean deprecated, String apiVersion) {
    /** 메서드가 없는 매핑 (모든 메서드). */
    public static final String ANY_METHOD = "*";

    /** 목록 파일의 원소를 읽는다. 메서드·경로가 없으면 null. */
    static Endpoint of(Map<?, ?> json) {
        Object method = json.get("method");
        Object path = json.get("path");

        if (method == null || path == null) {
            return null;
        }

        Object handler = json.get("handler");
        Object version = json.get("apiVersion");

        return new Endpoint(String.valueOf(method).toUpperCase(Locale.ROOT), String.valueOf(path),
            handler == null ? null : String.valueOf(handler), Boolean.TRUE.equals(json.get("deprecated")),
            version == null ? null : String.valueOf(version));
    }

    /** 보고서 JSON 의 원소. */
    Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("method", method);
        json.put("path", path);
        json.put("handler", handler);
        json.put("deprecated", deprecated);
        json.put("apiVersion", apiVersion);

        return json;
    }

    /** {@code GET /api/v1/users/{id}} (버전이 있으면 {@code (v1.1)} 을 붙인다). */
    public String label() {
        return method + " " + path + (apiVersion == null ? "" : " (v" + apiVersion + ")");
    }
}
