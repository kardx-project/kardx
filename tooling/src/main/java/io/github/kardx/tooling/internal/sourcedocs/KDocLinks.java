package io.github.kardx.tooling.internal.sourcedocs;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KDoc 링크를 글로 바꾼다: {@code [Foo]} → {@code Foo}, {@code [글][Foo]} → {@code 글}, {@code [글](https://…)} → {@code 글}.
 * 백틱 코드({@code `a[0]`}) 안은 그대로 둔다.
 */
final class KDocLinks {
    private static final Pattern LINK = Pattern.compile(
        "\\[([^\\[\\]]+)]\\[[^\\[\\]]*]" // [글][Foo]
            + "|\\[([^\\[\\]]+)]\\([^()\\s]*\\)" // [글](url)
            + "|\\[([A-Za-z_][\\w.#$]*)](?![(\\[])"); // [Foo], [Foo.bar]

    private KDocLinks() {
    }

    static String toText(String markdown) {
        StringBuilder out = new StringBuilder();
        String[] parts = markdown.split("`", -1);

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('`');
            }

            // 백틱 사이(홀수 번째)는 코드다
            out.append(i % 2 == 0 ? links(parts[i]) : parts[i]);
        }

        return out.toString();
    }

    private static String links(String text) {
        return LINK.matcher(text).replaceAll(link -> Matcher.quoteReplacement(label(link)));
    }

    private static String label(MatchResult link) {
        for (int group = 1; group <= link.groupCount(); group++) {
            if (link.group(group) != null) {
                return link.group(group);
            }
        }

        return link.group();
    }
}
