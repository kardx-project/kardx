package io.github.kardx.tooling.internal.sourcedocs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 주석 본문을 문단 목록으로 정리한다. 인라인 태그({@link InlineTags})를 풀고, Java 는 HTML 을({@link JavadocHtml}),
 * Kotlin 은 KDoc 링크를({@link KDocLinks}) 글로 바꾼다. 빈 줄(또는 블록 HTML 태그)이 문단을 나누고, 문단 안의 줄바꿈·
 * 공백은 한 칸이 된다.
 */
final class DocTextNormalizer {
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private DocTextNormalizer() {
    }

    static List<String> paragraphs(String text, SourceLanguage language) {
        List<String> paragraphs = new ArrayList<>();

        for (String paragraph : PARAGRAPH_BREAK.split(markupToText(text, language))) {
            String line = WHITESPACE.matcher(paragraph).replaceAll(" ").strip();

            if (!line.isEmpty()) {
                paragraphs.add(line);
            }
        }

        return paragraphs;
    }

    /** 인라인 태그 사이의 글만 언어별 마크업 처리를 한다 ({@code {@code <b>}} 의 내용은 그대로 둔다). */
    private static String markupToText(String text, SourceLanguage language) {
        StringBuilder out = new StringBuilder();
        int from = 0;
        int start = text.indexOf("{@");

        while (start >= 0) {
            int end = InlineTags.closingBrace(text, start);

            if (end < 0) {
                break;
            }

            out.append(plain(text.substring(from, start), language))
                .append(InlineTags.render(text.substring(start + 2, end)));
            from = end + 1;
            start = text.indexOf("{@", from);
        }

        return out.append(plain(text.substring(from), language)).toString();
    }

    private static String plain(String text, SourceLanguage language) {
        return language == SourceLanguage.JAVA ? JavadocHtml.toText(text) : KDocLinks.toText(text);
    }
}
