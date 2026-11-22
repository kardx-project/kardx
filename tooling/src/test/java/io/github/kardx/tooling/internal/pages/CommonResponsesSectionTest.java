package io.github.kardx.tooling.internal.pages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** index 문서 끝의 공통 오류 응답 표({@link CommonResponsesSection}). */
class CommonResponsesSectionTest {

    @Test
    void rendersOneAsciidocTableWithAnchorInEnglishByDefault() {
        String section = CommonResponsesSection.render(new TreeMap<>(Map.of(401, "No token", 403, "", 500, "a | b")),
            "ErrorResponse", DocsLanguage.EN);

        assertTrue(section.contains("[[common-error-responses]]\n== Common error responses\n"), section);
        assertTrue(section.contains("\nEvery API can return the responses below, even when its own documentation "
            + "does not list them.\n"), section);
        assertTrue(section.contains("\nOnly APIs that require authentication return `401`, `403`.\n"), section);
        assertTrue(section.contains("\nThe response body schema is `ErrorResponse`.\n"), section);
        assertTrue(section.contains("|===\n|Status code |Description\n"), section);
        assertTrue(section.contains("|`401`\n|No token\n\n|`403`\n|\n\n|`500`\n|a \\| b\n|===\n"),
            "상태 코드 순서, | 는 이스케이프\n" + section);
        assertEquals(1, section.split("\\|===", -1).length - 2, "표 하나\n" + section);
    }

    @Test
    void rendersKoreanTextsUnchangedForKo() {
        String section = CommonResponsesSection.render(new TreeMap<>(Map.of(401, "토큰 없음", 403, "권한 없음")),
            "ErrorResponse", DocsLanguage.KO);

        assertEquals(String.join("\n",
            "",
            "[[common-error-responses]]",
            "== 공통 오류 응답",
            "",
            "아래 응답은 API 마다 따로 적지 않았어도 모든 API 가 돌려줄 수 있습니다.",
            "`401`, `403` 은 인증이 필요한 API 에만 해당합니다.",
            "응답 본문 스키마는 `ErrorResponse` 입니다.",
            "",
            "[cols=\"1,4\",options=\"header\"]",
            "|===",
            "|상태 코드 |설명",
            "",
            "|`401`",
            "|토큰 없음",
            "",
            "|`403`",
            "|권한 없음",
            "|===",
            ""), section);
    }

    @Test
    void mentionsOnlyConfiguredAuthStatuses() {
        String english = CommonResponsesSection.render(new TreeMap<>(Map.of(401, "x", 500, "y")), null,
            DocsLanguage.EN);
        String korean = CommonResponsesSection.render(Map.of(403, "x"), null, DocsLanguage.KO);

        assertTrue(english.contains("\nOnly APIs that require authentication return `401`.\n"), english);
        assertFalse(english.contains("`403`"), english);
        assertTrue(korean.contains("\n`403` 은 인증이 필요한 API 에만 해당합니다.\n"), korean);
        assertFalse(korean.contains("`401`"), korean);
    }

    @Test
    void omitsAuthAndSchemaSentencesWhenNotRelevant() {
        String section = CommonResponsesSection.render(Map.of(500, "Server error"), null, DocsLanguage.EN);

        assertFalse(section.contains("401"), section);
        assertFalse(section.contains("authentication"), section);
        assertFalse(section.contains("schema"), section);
        assertEquals("", CommonResponsesSection.render(Map.of(), null, DocsLanguage.EN));
    }
}
