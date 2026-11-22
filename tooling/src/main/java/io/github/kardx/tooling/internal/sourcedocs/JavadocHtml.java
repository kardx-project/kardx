package io.github.kardx.tooling.internal.sourcedocs;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Javadoc 본문의 HTML 을 글로 바꾼다. 블록 태그({@code <p>}, {@code <br>}, {@code <li>} …)는 문단 나눔(빈 줄)이 되고 나머지
 * 태그는 지운다. 그 뒤 엔티티({@code &lt;}, {@code &#64;} …)를 글자로 바꾼다.
 */
final class JavadocHtml {
    private static final String PARAGRAPH = "\n\n";
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("</?([A-Za-z][A-Za-z0-9]*)\\b[^<>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z]+);");
    private static final Set<String> BLOCK_TAGS = Set.of("p", "br", "ul", "ol", "li", "dl", "dt", "dd", "div",
        "pre", "table", "tr", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "hr");
    private static final Map<String, String> NAMED_ENTITIES = Map.of(
        "lt", "<", "gt", ">", "amp", "&", "quot", "\"", "apos", "'", "nbsp", " ");

    private JavadocHtml() {
    }

    static String toText(String html) {
        String text = COMMENT.matcher(html).replaceAll("");

        text = TAG.matcher(text).replaceAll(tag -> BLOCK_TAGS.contains(tag.group(1).toLowerCase(Locale.ROOT))
            ? PARAGRAPH
            : "");

        return ENTITY.matcher(text).replaceAll(entity -> Matcher.quoteReplacement(decode(entity.group(1), entity)));
    }

    private static String decode(String name, MatchResult entity) {
        if (!name.startsWith("#")) {
            return NAMED_ENTITIES.getOrDefault(name, entity.group());
        }

        boolean hex = name.length() > 1 && (name.charAt(1) == 'x' || name.charAt(1) == 'X');

        try {
            int code = hex ? Integer.parseInt(name.substring(2), 16) : Integer.parseInt(name.substring(1));

            return new String(Character.toChars(code));
        } catch (IllegalArgumentException e) {
            // 숫자가 너무 크거나 코드 포인트가 아니면 원문을 그대로 둔다
            return entity.group();
        }
    }
}
