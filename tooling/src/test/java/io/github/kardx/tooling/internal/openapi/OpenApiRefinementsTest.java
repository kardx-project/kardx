package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiRefinementsTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String... lines) {
        return (Map<String, Object>) SwaggerYaml.load(String.join("\n", lines));
    }

    @SuppressWarnings("unchecked")
    private static Object at(Map<String, Object> node, String... keys) {
        Object current = node;

        for (String key : keys) {
            current = current instanceof List<?> list
                ? list.get(Integer.parseInt(key))
                : ((Map<String, Object>) current).get(key);
        }

        return current;
    }

    private static Map<String, Object> itemSpec() {
        return yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /items/{id}:",
            "    get:",
            "      summary: 품목 조회",
            "      operationId: item-test/get-item",
            "      parameters:",
            "      - name: id",
            "        in: path",
            "        schema: {type: string}",
            "      - name: size",
            "        in: query",
            "        schema: {type: string}",
            "      - name: Authorization",
            "        in: header",
            "        example: Bearer eyJhbGciOiJub25lIn0.e30.c2ln",
            "        schema: {type: string}",
            "      responses:",
            "        '200':",
            "          description: '200'",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/Item'}",
            "              examples:",
            "                item-test/get-item: {value: '{\"id\" : 1, \"name\" : \"a\"}'}",
            "        '404':",
            "          description: '404'",
            "          content:",
            "            application/json:",
            "              schema: {type: object}",
            "components:",
            "  schemas:",
            "    Item:",
            "      type: object",
            "      properties:",
            "        id: {type: number}",
            "        name: {type: string, nullable: true}",
            "        tags:",
            "          type: array",
            "          items:",
            "            oneOf: [{type: object}, {type: string}]",
            "  securitySchemes:",
            "    bearer: {type: http, scheme: bearer}",
            "");
    }

    private static Resource itemResource() {
        return new Resource("item-test/get-item", yaml(
            "operationId: item-test/get-item",
            "summary: 품목 조회",
            "request:",
            "  path: /items/{id}",
            "  method: GET",
            "  pathParameters:",
            "  - {name: id, attributes: {schema: {type: integer, format: int64}}}",
            "  queryParameters:",
            "  - name: size",
            "    attributes: {schema: {type: integer, format: int32, maximum: 100}, default: '20', example: '10'}",
            "  headers: []",
            "response:",
            "  status: 200",
            "  contentType: application/json",
            "  responseFields:",
            "  - {path: id, attributes: {schema: {type: integer, format: int64}}}",
            "  - path: name",
            "    attributes: {schema: {type: string, minLength: 1}, requiredByConstraint: true, constraintNote: 'Past - x'}",
            "  - {path: tags, attributes: {schema: {type: array, items: {type: string, maxLength: 5}}}}",
            ""), Map.of());
    }

    private static Resource notFoundResource() {
        return new Resource("item-test/get-item-not-found", yaml(
            "operationId: item-test/get-item-not-found",
            "summary: 품목 조회 - 없음",
            "request: {path: '/items/{id}', method: GET}",
            "response: {status: 404, contentType: application/json}",
            ""), Map.of("tag", Map.of("name", "품목", "description", "품목 관리")));
    }

    @Test
    void mergesTypeConstraintDefaultAndExampleHints() {
        Map<String, Object> document = itemSpec();

        int changed = OpenApiSchemaHints.transform(document, List.of(itemResource()), DocsLanguage.EN);

        assertEquals(5, changed);
        assertEquals(Map.of("type", "integer", "format", "int64"),
            at(document, "paths", "/items/{id}", "get", "parameters", "0", "schema"));
        assertEquals(20L, at(document, "paths", "/items/{id}", "get", "parameters", "1", "schema", "default"));
        assertEquals(10L, at(document, "paths", "/items/{id}", "get", "parameters", "1", "example"));
        assertEquals("int64", at(document, "components", "schemas", "Item", "properties", "id", "format"));
        assertEquals(Map.of("type", "string", "maxLength", 5),
            at(document, "components", "schemas", "Item", "properties", "tags", "items"));
        assertEquals(List.of("name"), at(document, "components", "schemas", "Item", "required"));
        assertNull(at(document, "components", "schemas", "Item", "properties", "name", "nullable"));
        assertEquals("Constraints: Past - x",
            at(document, "components", "schemas", "Item", "properties", "name", "description"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requiresChildrenOfOptionalParentsAndAlignsNullable() {
        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /orders:",
            "    post:",
            "      requestBody:",
            "        content:",
            "          application/json:",
            "            schema: {$ref: '#/components/schemas/Order'}",
            "      responses:",
            "        '200': {description: '200'}",
            "components:",
            "  schemas:",
            "    Order:",
            "      type: object",
            "      properties:",
            "        tags: {type: array, nullable: true, items: {type: string}}",
            "        memo: {type: string, nullable: true}",
            "        lines:",
            "          type: array",
            "          items:",
            "            type: object",
            "            properties:",
            "              sku: {type: string, nullable: true}",
            "              note: {type: string, nullable: true}",
            "");
        Resource resource = new Resource("order-test/create", yaml(
            "operationId: order-test/create",
            "request:",
            "  path: /orders",
            "  method: POST",
            "  contentType: application/json",
            "  requestFields:",
            "  - {path: tags, attributes: {nullable: false}}",
            "  - {path: memo, attributes: {schema: {type: string}}}",
            "  - {path: lines, attributes: {nullable: true, nullableItems: true}}",
            "  - {path: 'lines[].sku', attributes: {requiredInParent: true, nullable: false}}",
            "  - {path: 'lines[].note', attributes: {schema: {type: string}}}",
            "response: {status: 200}",
            ""), Map.of());

        OpenApiSchemaHints.transform(document, List.of(resource), DocsLanguage.EN);

        Map<String, Object> order = (Map<String, Object>) at(document, "components", "schemas", "Order");

        assertNull(at(order, "properties", "tags", "nullable"));
        assertEquals(true, at(order, "properties", "memo", "nullable"));
        assertEquals(true, at(order, "properties", "lines", "nullable"));
        assertNull(order.get("required"));
        assertEquals(List.of("sku"), at(order, "properties", "lines", "items", "required"));
        assertEquals(true, at(order, "properties", "lines", "items", "nullable"));
        assertNull(at(order, "properties", "lines", "items", "properties", "sku", "nullable"));
        assertEquals(true, at(order, "properties", "lines", "items", "properties", "note", "nullable"));
    }

    @Test
    void appliesDefaultExampleAndNoteToResponseHeaders() {
        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /items:",
            "    get:",
            "      responses:",
            "        '200':",
            "          description: '200'",
            "          headers:",
            "            X-Rate-Limit: {description: 남은 요청 수, schema: {type: string}}",
            "");
        Resource resource = new Resource("item-test/list", yaml(
            "operationId: item-test/list",
            "request: {path: /items, method: GET}",
            "response:",
            "  status: 200",
            "  headers:",
            "  - name: x-rate-limit",
            "    attributes: {schema: {type: integer}, default: '100', example: '99', constraintNote: 0 이상}",
            ""), Map.of());

        int changed = OpenApiSchemaHints.transform(document, List.of(resource), DocsLanguage.KO);
        String[] header = {"paths", "/items", "get", "responses", "200", "headers", "X-Rate-Limit"};

        assertEquals(1, changed);
        assertEquals(Map.of("type", "integer", "default", 100L), at(document, concat(header, "schema")));
        assertEquals(99L, at(document, concat(header, "example")));
        assertEquals("남은 요청 수\n\n제약: 0 이상", at(document, concat(header, "description")), "ko 는 예전 문구 그대로");

        OpenApiSchemaHints.transform(document, List.of(resource), DocsLanguage.EN);

        assertEquals("남은 요청 수\n\n제약: 0 이상", at(document, concat(header, "description")),
            "다른 언어로 이미 붙은 제약 문단은 다시 붙이지 않는다");
    }

    private static String[] concat(String[] keys, String last) {
        String[] all = Arrays.copyOf(keys, keys.length + 1);
        all[keys.length] = last;

        return all;
    }

    @Test
    void tidiesOperationsDeterministically() {
        Map<String, Object> document = itemSpec();
        List<Resource> resources = List.of(itemResource(), notFoundResource());

        OpenApiOperationTidy.transform(document, resources);

        Map<String, Object> operation = SpecMaps.operation(document, "/items/{id}", "get");

        assertEquals("item-test.get-item", operation.get("operationId"));
        assertEquals("OK", at(operation, "responses", "200", "description"));
        assertEquals("Not Found: 품목 조회 - 없음", at(operation, "responses", "404", "description"));
        assertEquals(Map.of("id", 1, "name", "a"),
            at(operation, "responses", "200", "content", "application/json", "examples", "item-test/get-item", "value"));
        assertEquals("품목 조회",
            at(operation, "responses", "200", "content", "application/json", "examples", "item-test/get-item", "summary"));
        assertEquals(List.of(), operation.get("security"));
        assertEquals(List.of(), at(document, "tags"), "operation 에 태그가 없으면 최상위 tags 도 비어 있다");
    }

    @Test
    void removesRequestValueExamplesOfSensitiveHeadersButKeepsUserExamples() {
        Map<String, Object> document = itemSpec();
        Map<String, Object> userExample = itemSpec();
        Resource withExample = new Resource("item-test/get-item", yaml(
            "operationId: item-test/get-item",
            "request:",
            "  path: /items/{id}",
            "  method: GET",
            "  headers:",
            "  - {name: Authorization, attributes: {example: 'Bearer my-dev-token', default: 'Bearer dev'}}",
            "response: {status: 200}",
            ""), Map.of());

        SensitiveHeaderExamples.remove(document);
        SensitiveHeaderExamples.remove(userExample);
        OpenApiSchemaHints.transform(userExample, List.of(withExample), DocsLanguage.EN);

        String[] authorization = {"paths", "/items/{id}", "get", "parameters", "2"};

        assertFalse(((Map<?, ?>) at(document, authorization)).containsKey("example"), "가짜 JWT 예시는 뺀다");
        assertEquals("Bearer my-dev-token", at(userExample, concat(authorization, "example")));
        assertEquals("Bearer dev", at(userExample, concat(concat(authorization, "schema"), "default")));
    }

    private static Map<String, Object> sharedUserSpec() {
        return yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /users/{id}:",
            "    get:",
            "      responses:",
            "        '200':",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/User'}",
            "  /users:",
            "    post:",
            "      responses:",
            "        '200':",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/User'}",
            "components:",
            "  schemas:",
            "    User:",
            "      type: object",
            "      properties:",
            "        name: {type: string, description: 이름}",
            "        phone: {type: string, description: 전화번호}",
            "        nick: {type: string, description: 별명}",
            "");
    }

    private static Resource userResource(String id, String path, String method, String fields) {
        return new Resource(id, yaml(
            "operationId: " + id,
            "request: {path: '" + path + "', method: " + method + "}",
            "response:",
            "  status: 200",
            "  contentType: application/json",
            "  responseFields: " + fields,
            ""), Map.of());
    }

    @Test
    void marksMaskedFieldsAndKeepsSharedSchemasFromSpreadingMarks() {
        Map<String, Object> document = sharedUserSpec();
        String annotated = "{path: name, attributes: {masked: true, maskingRule: '가운데 글자를 *'}}";
        Resource get = userResource("user/get", "/users/{id}", "GET", "["
            + annotated + ", {path: phone, attributes: {masked: true}}, {path: nick, attributes: {deprecated: true}}]");
        Resource create = userResource("user/create", "/users", "POST", "["
            + annotated + ", {path: phone, attributes: {}}, {path: nick, attributes: {}}]");

        OpenApiSchemaHints.transform(document, List.of(get, create), DocsLanguage.EN);

        String[] user = {"components", "schemas", "User", "properties"};

        assertEquals(true, at(document, concat(concat(user, "name"), "x-masked")));
        assertEquals("가운데 글자를 *", at(document, concat(concat(user, "name"), "x-masked-rule")));
        assertEquals("이름\n\nMasked: 가운데 글자를 *", at(document, concat(concat(user, "name"), "description")));
        assertNull(at(document, concat(concat(user, "phone"), "x-masked")), "한 테스트만 표시하면 번지지 않는다");
        assertEquals("전화번호", at(document, concat(concat(user, "phone"), "description")));
        assertNull(at(document, concat(concat(user, "nick"), "deprecated")), "Deprecated 도 같다");
    }

    @Test
    void marksMaskedWithoutRuleOnce() {
        Map<String, Object> document = sharedUserSpec();
        Resource get = userResource("user/get", "/users/{id}", "GET", "[{path: phone, attributes: {masked: true}}]");
        Resource again = userResource("user/get-2", "/users/{id}", "GET", "[{path: phone, attributes: {masked: true}}]");

        OpenApiSchemaHints.transform(document, List.of(get, again), DocsLanguage.EN);

        Map<?, ?> phone = (Map<?, ?>) at(document, "components", "schemas", "User", "properties", "phone");

        assertEquals("전화번호\n\nMasked", phone.get("description"));
        assertEquals(true, phone.get("x-masked"));
        assertFalse(phone.containsKey("x-masked-rule"));
    }

    @Test
    void normalizesAndDeduplicatesOperationIds() {
        assertEquals("a.b", OpenApiOperationIds.normalize("a/b/"));
        assertEquals("a.b-c", OpenApiOperationIds.normalize("a/b c"));
        // 한글 등 비영어 글자는 남긴다 (Kotest 테스트 이름, JUnit 백틱 메서드 이름)
        assertEquals("user-spec.사용자-조회", OpenApiOperationIds.normalize("user-spec/사용자 조회"));
        assertEquals("spec.닉네임이-같은", OpenApiOperationIds.normalize("spec/닉네임이-같은-"));

        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "tags: []",
            "paths:",
            "  /a: {get: {operationId: x/y, tags: [가]}}",
            "  /b: {get: {operationId: x/y/, tags: [가, 나]}}",
            "");

        OpenApiOperationTidy.transform(document, List.of(new Resource("x", Map.of(), Map.of(
            "tag", Map.of("name", "나", "description", "설명")))));

        assertEquals("x.y", at(document, "paths", "/a", "get", "operationId"));
        assertEquals(List.of(), document.get("security"));
        assertNull(at(document, "paths", "/a", "get", "security"));
        assertEquals("x.y-2", at(document, "paths", "/b", "get", "operationId"));
        assertEquals(List.of(Map.of("name", "가"), Map.of("name", "나", "description", "설명")), document.get("tags"));
    }

    @Test
    void mergedOperationIdIgnoresEpagesVersion() {
        assertEquals("t/get-item", OpenApiOperationIds.commonPrefix(List.of("t/get-item", "t/get-item-not-found")));
        assertEquals("t/root", OpenApiOperationIds.commonPrefix(List.of("t/root-list", "t/root-long")));
        assertEquals("", OpenApiOperationIds.commonPrefix(List.of("a/x", "b/y")));

        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /list: {get: {operationId: a/success-getb/root-list, responses: {'200': {description: OK}}}}",
            "");
        Resource first = new Resource("b/root-list", yaml(
            "operationId: b/root-list",
            "request: {path: /list, method: GET}",
            "response: {status: 200}",
            ""), Map.of());
        Resource second = new Resource("a/success-get", yaml(
            "operationId: a/success-get",
            "request: {path: /list, method: GET}",
            "response: {status: 200}",
            ""), Map.of());

        OpenApiOperationTidy.transform(document, List.of(first, second));

        assertEquals("a.success-get", at(document, "paths", "/list", "get", "operationId"));
    }

    @Test
    void addsMultipartCookiesAndValueBodies() {
        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /upload:",
            "    post:",
            "      responses:",
            "        '200':",
            "          description: OK",
            "          headers: {Set-Cookie: {description: 세션 쿠키, schema: {type: string}}}",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/upload123'}",
            "components:",
            "  schemas:",
            "    upload123: {type: object}",
            "");
        Map<String, Object> extras = yaml(
            "requestParts:",
            "- {name: file, description: 파일, optional: false, contentType: image/png,"
                + " schema: {type: string, format: binary}}",
            "- {name: meta, optional: true, contentType: application/json, schema: {type: object}}",
            "requestCookies: [{name: SESSION, description: 세션, optional: false}]",
            "responseCookies: [{name: SESSION, description: 새 세션, optional: false}]",
            "responseBody: {type: integer, format: int64}",
            "");
        Resource resource = new Resource("upload", yaml(
            "request: {path: /upload, method: POST}",
            "response: {status: 200, contentType: application/json}",
            ""), extras);

        assertEquals(1, OpenApiRequestExtras.transform(document, List.of(resource), DocsLanguage.EN));

        Map<String, Object> operation = SpecMaps.operation(document, "/upload", "post");
        Map<String, Object> multipart = SpecMaps.map(at(operation, "requestBody", "content", "multipart/form-data"));

        assertEquals(List.of("requestBody", "responses", "parameters"), List.copyOf(operation.keySet()));
        assertEquals(List.of("file"), at(multipart, "schema", "required"));
        assertEquals(Map.of("type", "string", "format", "binary", "description", "파일"),
            at(multipart, "schema", "properties", "file"));
        assertEquals(Map.of("meta", Map.of("contentType", "application/json")), multipart.get("encoding"));
        assertEquals("cookie", at(operation, "parameters", "0", "in"));
        assertTrue(String.valueOf(at(operation, "responses", "200", "headers", "Set-Cookie", "description"))
            .contains("Cookies:\n\n`SESSION`: 새 세션"));
        assertEquals(Map.of("type", "integer", "format", "int64"),
            at(operation, "responses", "200", "content", "application/json", "schema"));
        assertFalse(SpecMaps.map(at(document, "components", "schemas")).containsKey("upload123"),
            "아무도 가리키지 않게 된 빈 스키마는 지운다");
    }

    @Test
    void writesCookieListInConfiguredLanguageOnce() {
        Map<String, Object> korean = cookieSpec();
        Map<String, Object> english = cookieSpec();
        Resource resource = new Resource("login", yaml(
            "request: {path: /login, method: POST}",
            "response: {status: 200}",
            ""), yaml("responseCookies: [{name: SESSION, description: 새 세션, optional: true}]", ""));
        String[] description = {"paths", "/login", "post", "responses", "200", "headers", "Set-Cookie", "description"};

        assertEquals(1, OpenApiRequestExtras.transform(korean, List.of(resource), DocsLanguage.KO));
        assertEquals(1, OpenApiRequestExtras.transform(english, List.of(resource), DocsLanguage.EN));
        assertEquals("세션 쿠키\n\n쿠키:\n\n`SESSION` (선택): 새 세션", at(korean, description), "ko 는 예전 문구 그대로");
        assertEquals("세션 쿠키\n\nCookies:\n\n`SESSION` (optional): 새 세션", at(english, description));
        assertEquals(0, OpenApiRequestExtras.transform(korean, List.of(resource), DocsLanguage.EN),
            "다른 언어로 이미 붙은 쿠키 목록은 다시 붙이지 않는다");
    }

    private static Map<String, Object> cookieSpec() {
        return yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /login:",
            "    post:",
            "      responses:",
            "        '200':",
            "          description: OK",
            "          headers: {Set-Cookie: {description: 세션 쿠키, schema: {type: string}}}",
            "");
    }
}
