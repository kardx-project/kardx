package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenApiFileBodiesTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String... lines) {
        return (Map<String, Object>) SwaggerYaml.load(String.join("\n", lines));
    }

    private static Map<String, Object> downloadSpec() {
        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /manual:",
            "    get:",
            "      responses:",
            "        '200':",
            "          description: OK",
            "          headers:",
            "            Content-Disposition: {description: 파일 이름, schema: {type: string}}",
            "          content:",
            "            application/pdf;charset=UTF-8:",
            "              schema: {$ref: '#/components/schemas/manual-1234'}",
            "  /image:",
            "    put:",
            "      requestBody:",
            "        content:",
            "          image/png:",
            "            schema: {$ref: '#/components/schemas/image-5678'}",
            "      responses:",
            "        '200':",
            "          description: OK",
            "          content:",
            "            text/plain;charset=UTF-8:",
            "              schema: {$ref: '#/components/schemas/image-9999'}",
            "components:",
            "  schemas:",
            "    manual-1234: {type: object}",
            "    image-5678: {type: object}",
            "    image-9999: {type: object}",
            "");

        OpenApiBodySchemas.transform(document);

        return document;
    }

    private static Resource download(Map<String, Object> extras) {
        return new Resource("file-test/download", yaml(
            "request: {path: /manual, method: GET}",
            "response:",
            "  status: 200",
            "  contentType: application/pdf;charset=UTF-8",
            "  headers:",
            "  - {name: Content-Disposition, example: 'attachment; filename=\"manual.pdf\"'}",
            ""), extras);
    }

    private static Resource upload(Map<String, Object> extras) {
        return new Resource("file-test/upload", yaml(
            "request: {path: /image, method: PUT, contentType: image/png}",
            "response: {status: 200, contentType: text/plain;charset=UTF-8}",
            ""), extras);
    }

    @Test
    void describesFileBodiesAndDropsCharsetOfBinaryMediaTypes() {
        Map<String, Object> document = downloadSpec();
        List<Resource> resources = List.of(
            download(Map.of("responseFile", Map.of("description", "제품 설명서 PDF"))),
            upload(Map.of("requestFile", Map.of("description", "업로드할 이미지"))));

        assertEquals(2, OpenApiFileBodies.transform(document, resources));

        Map<String, Object> manual = SpecMaps.response(SpecMaps.operation(document, "/manual", "get"), 200);
        Map<String, Object> image = SpecMaps.operation(document, "/image", "put");

        assertEquals(Map.of("application/pdf", Map.of("schema",
                Map.of("type", "string", "format", "binary", "description", "제품 설명서 PDF"))),
            manual.get("content"));
        assertEquals("attachment; filename=\"manual.pdf\"",
            SpecMaps.map(SpecMaps.map(manual, "headers"), "Content-Disposition").get("example"));
        assertEquals(Map.of("type", "string", "format", "binary", "description", "업로드할 이미지"),
            SpecMaps.map(SpecMaps.requestMedia(image, "image/png"), "schema"));
        assertEquals(Map.of("type", "string"),
            SpecMaps.map(SpecMaps.responseMedia(image, 200, "text/plain;charset=UTF-8"), "schema"),
            "글자 본문은 charset 을 그대로 두고 설명이 없으면 손대지 않는다");
    }

    @Test
    void binaryBodiesWithoutDescriptionStillLoseCharsetAndGetDispositionExample() {
        Map<String, Object> document = downloadSpec();

        assertEquals(1, OpenApiFileBodies.transform(document, List.of(download(Map.of()))));

        Map<String, Object> manual = SpecMaps.response(SpecMaps.operation(document, "/manual", "get"), 200);

        assertEquals(Map.of("application/pdf", Map.of("schema", Map.of("type", "string", "format", "binary"))),
            manual.get("content"));
        assertEquals("attachment; filename=\"manual.pdf\"",
            SpecMaps.map(SpecMaps.map(manual, "headers"), "Content-Disposition").get("example"));
        assertEquals(0, OpenApiFileBodies.transform(document, List.of(download(Map.of()))), "두 번 적용해도 같다");
    }

    @Test
    void replacesEmptyJsonSchemaOfDescribedFileButKeepsSharedSchemas() {
        Map<String, Object> document = yaml(
            "openapi: 3.0.1",
            "paths:",
            "  /export:",
            "    get:",
            "      responses:",
            "        '200':",
            "          description: OK",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/export-1'}",
            "  /item:",
            "    get:",
            "      responses:",
            "        '200':",
            "          description: OK",
            "          content:",
            "            application/json:",
            "              schema: {$ref: '#/components/schemas/Item'}",
            "components:",
            "  schemas:",
            "    export-1: {type: object}",
            "    Item: {type: object, properties: {id: {type: integer}}}",
            "");
        Map<String, Object> file = Map.of("responseFile", Map.of("description", "내보낸 JSON 파일"));
        Resource export = new Resource("file-test/export", yaml(
            "request: {path: /export, method: GET}",
            "response: {status: 200, contentType: application/json}",
            ""), file);
        Resource item = new Resource("file-test/item", yaml(
            "request: {path: /item, method: GET}",
            "response: {status: 200, contentType: application/json}",
            ""), file);

        assertEquals(1, OpenApiFileBodies.transform(document, List.of(export, item)));
        assertEquals(Map.of("type", "string", "description", "내보낸 JSON 파일"), SpecMaps.map(SpecMaps.responseMedia(
            SpecMaps.operation(document, "/export", "get"), 200, "application/json"), "schema"));
        assertNull(SpecMaps.componentSchemas(document).get("export-1"), "가리키는 곳이 없어진 빈 스키마는 지운다");
        assertEquals(Map.of("$ref", "#/components/schemas/Item"), SpecMaps.map(SpecMaps.responseMedia(
            SpecMaps.operation(document, "/item", "get"), 200, "application/json"), "schema"));
    }
}
