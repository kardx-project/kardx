package io.github.kardx.tooling.internal.sourcedocs;

/**
 * Javadoc 인라인 태그 하나를 글로 바꾼다: {@code {@link Foo}} → {@code Foo}, {@code {@link Foo#bar(int) 라벨}} → {@code 라벨},
 * {@code {@link #bar}} → {@code bar}, {@code {@code x}} → {@code `x`}, {@code {@literal x}} → {@code x},
 * {@code {@inheritDoc}}·{@code {@docRoot}} → 빈 글. 모르는 태그는 내용만 남긴다.
 */
final class InlineTags {
    private InlineTags() {
    }

    /** {@code {@} 의 {@code {} 위치에서 짝이 되는 {@code }} 위치. 닫히지 않으면 -1. */
    static int closingBrace(String text, int open) {
        int depth = 0;

        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }

        return -1;
    }

    /** @param body {@code {@} 와 {@code }} 사이 ({@code link Foo label}) */
    static String render(String body) {
        int split = 0;

        while (split < body.length() && !Character.isWhitespace(body.charAt(split))) {
            split++;
        }

        String name = body.substring(0, split);
        String content = body.substring(split).strip();

        return switch (name) {
            case "code" -> "`" + content + "`";
            case "link", "linkplain", "value", "see" -> link(content);
            case "inheritDoc", "docRoot" -> "";
            default -> content;
        };
    }

    /** 참조와 라벨. 라벨이 있으면 라벨, 없으면 참조를 읽기 좋게 ({@code Foo#bar(int)} → {@code Foo.bar}). */
    private static String link(String content) {
        int depth = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth == 0 && Character.isWhitespace(c)) {
                return content.substring(i).strip();
            }
        }

        return reference(content);
    }

    private static String reference(String reference) {
        int parameters = reference.indexOf('(');
        String member = parameters >= 0 ? reference.substring(0, parameters) : reference;

        return member.startsWith("#") ? member.substring(1) : member.replace('#', '.');
    }
}
