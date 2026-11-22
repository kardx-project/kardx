package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import io.github.kardx.tooling.internal.spec.SwaggerYaml;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code openApiInfo { }} 후처리({@link OpenApiInfoMetadata}). */
class OpenApiInfoMetadataTest {
    /** epages 결과처럼 info 에 제목·버전만 있고, 태그는 operation 이 쓴 것(Orders 는 @AsciiDescription 설명)이다. */
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /users: {get: {tags: [Users], responses: {"200": {description: OK}}}}
          /orders: {get: {tags: [Orders], responses: {"200": {description: OK}}}}
          /cart: {get: {tags: [Cart], responses: {"200": {description: OK}}}}
        tags:
        - {name: Cart}
        - {name: Orders, description: From AsciiDescription}
        - {name: Users}
        """;

    @Test
    void fillsInfoAndTopLevelExternalDocs() {
        Map<String, Object> document = document();
        List<String> warnings = new ArrayList<>();
        OpenApiInfoSettings settings = new OpenApiInfoSettings("# Shop\n\nIntro", "https://example.com/terms",
            values("name", "API team", "url", null, "email", "api@example.com"),
            values("name", "Apache 2.0", "url", "https://www.apache.org/licenses/LICENSE-2.0"),
            values("url", "https://wiki.example.com", "description", "Guide"), List.of(), Map.of(), Map.of(), Map.of());

        int changed = OpenApiInfoMetadata.transform(document, settings, DocsLanguage.EN, warnings);
        Map<String, Object> info = SpecMaps.map(document, "info");

        assertEquals(5, changed);
        assertEquals("shop", info.get("title"), "epages 값은 그대로");
        assertEquals("# Shop\n\nIntro", info.get("description"));
        assertEquals("https://example.com/terms", info.get("termsOfService"));
        assertEquals(Map.of("name", "API team", "email", "api@example.com"), info.get("contact"), "빈 값은 넣지 않는다");
        assertEquals(List.of("name", "url"), List.copyOf(SpecMaps.map(info, "license").keySet()));
        assertEquals(List.of("description", "url"), List.copyOf(SpecMaps.map(document, "externalDocs").keySet()));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void leavesTheSpecAloneWhenNothingIsSet() {
        Map<String, Object> document = document();
        String before = SwaggerYaml.dump(document);

        assertEquals(0, OpenApiInfoMetadata.transform(document, OpenApiInfoSettings.NONE, DocsLanguage.EN,
            new ArrayList<>()));
        assertEquals(before, SwaggerYaml.dump(document));
    }

    @Test
    void warnsWhenARequiredValueIsMissing() {
        Map<String, Object> document = document();
        List<String> warnings = new ArrayList<>();
        OpenApiInfoSettings settings = new OpenApiInfoSettings(null, null, Map.of(),
            values("name", null, "url", "https://license"), values("url", " ", "description", "Guide"),
            List.of(), Map.of(), Map.of(), Map.of());

        OpenApiInfoMetadata.transform(document, settings, DocsLanguage.EN, warnings);

        assertNull(SpecMaps.map(document, "info").get("license"));
        assertFalse(document.containsKey("externalDocs"));
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("license needs a name"), warnings.toString());
    }

    @Test
    void ordersAndDescribesTagsAndWarnsAboutUnusedOnes() {
        Map<String, Object> document = document();
        List<String> warnings = new ArrayList<>();
        Map<String, String> externalDocs = new LinkedHashMap<>();

        externalDocs.put("Users.url", "https://wiki/users");
        externalDocs.put("Users.description", "");
        OpenApiInfoSettings settings = new OpenApiInfoSettings(null, null, Map.of(), Map.of(), Map.of(),
            List.of("Users", "Payments", "Orders"), Map.of("Users", "Profiles", "Orders", "Build file wins"),
            externalDocs, Map.of());

        OpenApiInfoMetadata.transform(document, settings, DocsLanguage.EN, warnings);
        List<Map<String, Object>> tags = SpecMaps.maps(document.get("tags"));

        assertEquals(List.of("Users", "Orders", "Cart"), tags.stream().map(tag -> tag.get("name")).toList(),
            "정한 순서가 앞, 나머지는 원래 순서");
        assertEquals("Profiles", tags.get(0).get("description"));
        assertEquals(Map.of("url", "https://wiki/users"), tags.get(0).get("externalDocs"));
        assertEquals("Build file wins", tags.get(1).get("description"), "빌드 설정이 @AsciiDescription 을 이긴다");
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("tag 'Payments' is not used"), warnings.toString());
    }

    @Test
    void groupsTagsAndCollectsTheRestInALastGroup() {
        Map<String, Object> document = document();
        List<String> warnings = new ArrayList<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();

        groups.put("Shopping", List.of("Orders", "Ghost"));
        groups.put("Empty", List.of("Ghost"));
        OpenApiInfoSettings settings = new OpenApiInfoSettings(null, null, Map.of(), Map.of(), Map.of(), List.of(),
            Map.of(), Map.of(), groups);

        OpenApiInfoMetadata.transform(document, settings, DocsLanguage.KO, warnings);

        assertEquals(List.of(
            Map.of("name", "Shopping", "tags", List.of("Orders")),
            Map.of("name", "기타", "tags", List.of("Cart", "Users"))), document.get("x-tagGroups"),
            "Redoc 이 그룹 없는 태그를 숨기지 않게 남은 태그를 모은다");
        assertEquals(List.of("tag 'Ghost' is not used by any documented operation, so it was left out. Tags come "
            + "from the test class @DisplayName or openApi { tag(...) }"), warnings);
    }

    private static Map<String, Object> document() {
        return SpecMaps.map(SwaggerYaml.load(SPEC));
    }

    /** null 값을 허용하는 순서 있는 맵 (키, 값, 키, 값 …). */
    private static Map<String, String> values(String... keysAndValues) {
        Map<String, String> values = new LinkedHashMap<>();

        for (int i = 0; i < keysAndValues.length; i += 2) {
            values.put(keysAndValues[i], keysAndValues[i + 1]);
        }

        return values;
    }
}
