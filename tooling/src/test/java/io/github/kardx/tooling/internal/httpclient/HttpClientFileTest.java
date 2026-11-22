package io.github.kardx.tooling.internal.httpclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 문서화한 요청 → IntelliJ HTTP Client 파일 ({@link HttpClientRequests}, {@link HttpClientFile}). */
class HttpClientFileTest {
    @TempDir
    File snippets;

    @Test
    void writesPrimaryRecordedRequestPerOperation() throws IOException {
        File created = fragment("order-docs/create-order", "POST", "/orders", 201, "주문 생성", """
            [source,http,options="nowrap"]
            ----
            POST /orders?dryRun=false HTTP/1.1
            Content-Type: application/json;charset=UTF-8
            Authorization: Bearer ****
            Content-Length: 17
            Host: localhost:8080

            {
              "sku" : "A-1"
            }
            ----
            """);
        File invalid = fragment("order-docs/create-order-invalid", "POST", "/orders", 400, "주문 생성 실패", """
            ----
            POST /orders HTTP/1.1
            Host: localhost

            {}
            ----
            """);
        File item = fragment("order-docs/get-order", "GET", "/orders/{id}", 200, "주문 조회", null);
        File spec = new File(snippets, "openapi3.yaml");

        Files.writeString(spec.toPath(), """
            openapi: 3.0.1
            paths:
              /orders:
                post: {operationId: createOrder}
              /orders/{id}:
                get: {summary: 주문 한 건 조회}
            """, StandardCharsets.UTF_8);

        List<HttpClientRequests.Entry> entries = HttpClientRequests.collect(List.of(invalid, item, created),
            List.of(new File(snippets, "missing.yaml"), spec));

        assertEquals("""
            ### 주문 생성
            # @name createOrder
            POST {{baseUrl}}/orders?dryRun=false
            Content-Type: application/json;charset=UTF-8
            Authorization: Bearer {{bearerToken}}

            {
              "sku" : "A-1"
            }

            ### 주문 한 건 조회
            # @name order-docs-get-order
            GET {{baseUrl}}/orders/{id}
            X-Request-Id: req-1
            """, HttpClientFile.render(entries),
            "성공 응답 테스트를 고르고, 스니펫이 없으면 resource.json 으로 만든다. 제목은 스펙의 summary 를 따른다");
        assertEquals(Map.of("local", Map.of("baseUrl", "http://localhost:8080", "bearerToken", "")),
            HttpClientFile.environment(entries));
    }

    @Test
    void replacesMaskedValuesWithVariables() throws IOException {
        File me = fragment("auth/me", "GET", "/me", 200, "내 정보", """
            ----
            GET /me?access_token=****&page=1&sig=%2A%2A%2A%2A HTTP/1.1
            Cookie: SESSION=****; theme=dark
            X-Api-Key: ****
            Proxy-Authorization: Basic ****
            X-Note: a****b
            Host: localhost

            ----
            """);
        File login = fragment("auth/login", "POST", "/login", 200, "로그인", """
            ----
            POST /login HTTP/1.1
            Content-Type: application/x-www-form-urlencoded

            username=hong&password=****&client_secret=%2A%2A%2A%2A
            ----
            """);
        File json = fragment("auth/json", "PUT", "/json", 200, "JSON", """
            ----
            PUT /json HTTP/1.1
            Content-Type: application/json

            {"password":"****"}
            ----
            """);
        List<HttpClientRequests.Entry> entries = HttpClientRequests.collect(List.of(me, login, json), List.of());

        assertEquals("""
            ### JSON
            # @name auth-json
            PUT {{baseUrl}}/json
            Content-Type: application/json

            {"password":"****"}

            ### 로그인
            # @name auth-login
            POST {{baseUrl}}/login
            Content-Type: application/x-www-form-urlencoded

            username=hong&password={{password}}&client_secret={{client_secret}}

            ### 내 정보
            # @name auth-me
            GET {{baseUrl}}/me?access_token={{access_token}}&page=1&sig={{sig}}
            Cookie: SESSION={{SESSION}}; theme=dark
            X-Api-Key: {{x_api_key}}
            Proxy-Authorization: Basic {{proxy_authorization}}
            X-Note: a****b
            """, HttpClientFile.render(entries), "가린 값만 변수로 바꾸고 JSON 본문·일부만 가린 값은 그대로 둔다");

        Map<String, Object> secrets = new LinkedHashMap<>();

        List.of("SESSION", "access_token", "client_secret", "password", "proxy_authorization", "sig", "x_api_key")
            .forEach(name -> secrets.put(name, ""));

        Map<String, Object> local = new LinkedHashMap<>(Map.of("baseUrl", "http://localhost:8080"));

        local.putAll(secrets);

        assertEquals(Map.of("local", local), HttpClientFile.environment(entries));
        assertEquals(Map.of("local", secrets), HttpClientFile.privateEnvironment(entries));
    }

    @Test
    void keepsVariableNamesValidAndClearOfReservedNames() {
        RecordedRequest request = new RecordedRequest("GET", "/x?baseUrl=****&a.b%5B0%5D=****&=****",
            List.of("Cookie: 1st=****", "X: ****"), "");
        List<HttpClientRequests.Entry> entries = List.of(new HttpClientRequests.Entry("x", "x", request));

        assertEquals("""
            ### x
            # @name x
            GET {{baseUrl}}/x?baseUrl={{baseUrl_value}}&a.b%5B0%5D={{a_b_5B0_5D}}&={{masked}}
            Cookie: 1st={{_1st}}
            X: {{x}}
            """, HttpClientFile.render(entries));
    }

    @Test
    void writesNoPrivateEnvironmentWithoutSecrets() {
        RecordedRequest request = new RecordedRequest("GET", "/x?page=1", List.of("Accept: */*"), "");
        List<HttpClientRequests.Entry> entries = List.of(new HttpClientRequests.Entry("x", "x", request));

        assertEquals(Map.of("local", Map.of("baseUrl", "http://localhost:8080")), HttpClientFile.environment(entries));
        assertEquals(Map.of(), HttpClientFile.privateEnvironment(entries));
    }

    private File fragment(String directory, String method, String path, int status, String summary, String snippet)
        throws IOException {
        File dir = new File(snippets, directory);
        File resource = new File(dir, HttpClientRequests.RESOURCE_FILE);

        Files.createDirectories(dir.toPath());
        Files.writeString(resource.toPath(), """
            { "operationId": "%s", "summary": "%s",
              "request": { "path": "%s", "method": "%s", "contentType": null,
                           "headers": [ { "name": "X-Request-Id", "example": "req-1" } ], "example": null },
              "response": { "status": %d } }
            """.formatted(directory, summary, path, method, status), StandardCharsets.UTF_8);

        if (snippet != null) {
            Files.writeString(new File(dir, HttpClientRequests.REQUEST_SNIPPET).toPath(), snippet,
                StandardCharsets.UTF_8);
        }

        return resource;
    }
}
