package io.github.kardx.tooling.internal.pages;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.i18n.DocsText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REST Docs index 문서({@code index.adoc}) 끝에 붙이는 공통 오류 응답 절({@link DocsText#COMMON_RESPONSES_TITLE}).
 * 플러그인 확장 {@code openApiCommonResponses} 를 operation 마다 되풀이하지 않고 HTML 에 한 번만 보여 준다.
 * 문구는 {@code kardx.language} 를 따른다.
 */
public final class CommonResponsesSection {
    /** 절의 앵커. 직접 쓴 문서에서 {@code <<common-error-responses>>} 로 링크한다. */
    public static final String ANCHOR = "common-error-responses";
    /** 인증이 필요한 API 에만 해당하는 상태 코드. 정한 것만 안내 문장에 넣는다. */
    private static final List<Integer> AUTH_STATUSES = List.of(401, 403);

    private CommonResponsesSection() {
    }

    /**
     * @param descriptions 상태 코드 순서의 상태 코드 → 설명. 비었으면 빈 글
     * @param schema 응답 본문 스키마 이름. 모르면 null (스키마 문장을 넣지 않는다)
     * @param language 절 제목·안내 문장·표 머리글의 언어
     */
    public static String render(Map<Integer, String> descriptions, String schema, DocsLanguage language) {
        if (descriptions.isEmpty()) {
            return "";
        }

        List<String> lines = new ArrayList<>(List.of(
            "",
            "[[" + ANCHOR + "]]",
            "== " + DocsText.COMMON_RESPONSES_TITLE.in(language),
            "",
            DocsText.COMMON_RESPONSES_INTRO.in(language)));
        List<String> auth = AUTH_STATUSES.stream()
            .filter(descriptions::containsKey)
            .map(status -> "`" + status + "`")
            .toList();

        if (!auth.isEmpty()) {
            lines.add(DocsText.COMMON_RESPONSES_AUTH.format(language, String.join(", ", auth)));
        }

        if (schema != null) {
            lines.add(DocsText.COMMON_RESPONSES_SCHEMA.format(language, schema));
        }

        lines.addAll(List.of("", "[cols=\"1,4\",options=\"header\"]", "|===",
            DocsText.COMMON_RESPONSES_HEADER.in(language)));
        descriptions.forEach((status, description) -> lines.addAll(List.of(
            "",
            "|`" + status + "`",
            "|" + cell(description))));
        lines.add("|===");

        return String.join("\n", lines) + "\n";
    }

    /** 표 칸 구분자({@code |})가 내용을 끊지 않게 한다. */
    private static String cell(String text) {
        return text.replace("|", "\\|");
    }
}
