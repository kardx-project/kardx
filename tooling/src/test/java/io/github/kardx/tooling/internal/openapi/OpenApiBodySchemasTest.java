package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiBodySchemasTest {

    @Test
    @SuppressWarnings("unchecked")
    void replacesEmptyObjectSchemasOfNonJsonBodies() {
        Map<String, Object> document = (Map<String, Object>) SwaggerYaml.load(String.join("\n",
            "openapi: 3.0.1",
            "paths:",
            "  /files/{name}:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            application/octet-stream:",
            "              schema:",
            "                $ref: '#/components/schemas/files-name123'",
            "  /hello:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            text/plain;charset=UTF-8:",
            "              schema:",
            "                $ref: '#/components/schemas/files-name123'",
            "  /users:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            application/json:",
            "              schema:",
            "                $ref: '#/components/schemas/Empty'",
            "components:",
            "  schemas:",
            "    files-name123:",
            "      type: object",
            "    Empty:",
            "      type: object",
            ""));

        int changed = OpenApiBodySchemas.transform(document);
        String yaml = SwaggerYaml.dump(document);

        assertEquals(2, changed);
        assertTrue(yaml.contains("""
            application/octet-stream:
                          schema:
                            type: string
                            format: binary"""), yaml);
        assertTrue(yaml.contains("text/plain;charset=UTF-8:\n              schema:\n                type: string\n"), yaml);
        assertFalse(yaml.contains("files-name123"), "아무도 가리키지 않는 빈 스키마는 지운다");
        assertTrue(yaml.contains("Empty:"), "JSON 본문 스키마는 그대로 둔다");
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsReplacedSchemaWithNonAsciiNameWhileStillReferenced() {
        Map<String, Object> document = (Map<String, Object>) SwaggerYaml.load(String.join("\n",
            "openapi: 3.0.1",
            "paths:",
            "  /files:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            application/octet-stream:",
            "              schema:",
            "                $ref: '#/components/schemas/빈본문'",
            "            application/json:",
            "              schema:",
            "                $ref: '#/components/schemas/빈본문'",
            "  /unused:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            text/plain:",
            "              schema:",
            "                $ref: '#/components/schemas/안쓰는본문'",
            "components:",
            "  schemas:",
            "    빈본문:",
            "      type: object",
            "    안쓰는본문:",
            "      type: object",
            ""));

        OpenApiBodySchemas.transform(document);
        Map<String, Object> schemas = SpecMaps.componentSchemas(document);

        assertTrue(schemas.containsKey("빈본문"), "JSON 본문이 아직 가리키는 한글 이름 스키마는 남긴다");
        assertFalse(schemas.containsKey("안쓰는본문"), "아무도 가리키지 않게 된 한글 이름 스키마는 지운다");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dropsExamplesOfBinaryBodiesButKeepsTextExamples() {
        Map<String, Object> document = (Map<String, Object>) SwaggerYaml.load(String.join("\n",
            "openapi: 3.0.1",
            "paths:",
            "  /manual:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            application/pdf:",
            "              schema:",
            "                $ref: '#/components/schemas/manual123'",
            "              examples:",
            "                manual:",
            "                  value: '<binary: 45 bytes>'",
            "  /export:",
            "    get:",
            "      responses:",
            "        \"200\":",
            "          content:",
            "            text/csv:",
            "              schema:",
            "                $ref: '#/components/schemas/manual123'",
            "              examples:",
            "                export:",
            "                  value: 'id,name'",
            "components:",
            "  schemas:",
            "    manual123:",
            "      type: object",
            ""));

        OpenApiBodySchemas.transform(document);
        String yaml = SwaggerYaml.dump(document);

        assertFalse(yaml.contains("<binary: 45 bytes>"), "바이너리 본문의 예시는 지운다");
        assertTrue(yaml.contains("id,name"), "글자 본문(text/csv)의 예시는 남긴다");
    }

    @Test
    void collectsReferencedSchemaNamesFromTheWholeTree() {
        Map<String, Object> document = Map.of(
            "a", List.of(Map.of("$ref", "#/components/schemas/주문")),
            "b", Map.of("items", Map.of("$ref", "#/components/schemas/Item")),
            "c", Map.of("$ref", "#/components/parameters/Page"));

        assertEquals(Set.of("주문", "Item"), SpecMaps.referencedSchemaNames(document));
    }
}
