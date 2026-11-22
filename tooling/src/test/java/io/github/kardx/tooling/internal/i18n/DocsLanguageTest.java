package io.github.kardx.tooling.internal.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.kardx.tooling.ToolingException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocsLanguageTest {

    @Test
    void parsesSupportedCodesIgnoringCase() {
        assertEquals(DocsLanguage.KO, DocsLanguage.of("ko"));
        assertEquals(DocsLanguage.KO, DocsLanguage.of(" KO "));
        assertEquals(DocsLanguage.EN, DocsLanguage.of("En"));
        assertEquals("ko", DocsLanguage.KO.code());
    }

    @Test
    void rejectsUnsupportedCodesWithSupportedValues() {
        ToolingException error = assertThrows(ToolingException.class, () -> DocsLanguage.of("fr"));

        assertEquals("kardx.language must be en or ko, got 'fr'", error.getMessage());
        assertThrows(ToolingException.class, () -> DocsLanguage.of(null));
    }

    @Test
    void textsShareLabelsWithCoreAndKeepKoreanWording() {
        assertEquals("Constraints: ", DocsText.CONSTRAINTS.in(DocsLanguage.EN), "core 의 en 머리와 같다");
        assertEquals("제약: ", DocsText.CONSTRAINTS.in(DocsLanguage.KO), "core 의 ko 머리와 같다");
        assertEquals("(optional)", DocsText.OPTIONAL.in(DocsLanguage.EN));
        assertEquals("(선택)", DocsText.OPTIONAL.in(DocsLanguage.KO));
        assertEquals(List.of("Cookies:", "쿠키:"), DocsText.COOKIES.variants());
        assertEquals("응답 본문 스키마는 `E` 입니다.", DocsText.COMMON_RESPONSES_SCHEMA.format(DocsLanguage.KO, "E"));
    }
}
