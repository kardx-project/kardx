package io.github.kardx.tooling.internal.pages;

import java.util.regex.Pattern;

/**
 * 플러그인 리소스 HTML 템플릿에서 주석을 지운다. 템플릿 주석은 개발자용(한국어)이라, 생성 문서의 언어와 상관없이 사용자 결과에
 * 남지 않게 한다. 스펙·제목 같은 값을 넣기 전의 템플릿에만 쓴다.
 *
 * <p>JS·CSS 를 해석하지 않으므로 확실히 주석인 것만 지운다.
 * <ul>
 *   <li>HTML 주석 {@code <!-- … -->}. 조건부 주석({@code <!--[if …]>}, {@code <!--<![endif]-->}, {@code <!-->})은 남긴다</li>
 *   <li>줄 전체가 {@code // 설명} 인 줄 ({@code //} 뒤가 공백이거나 줄 끝). {@code //cdn.example.com} 같은 주소와
 *       코드 뒤에 붙은 {@code //} 는 건드리지 않는다</li>
 *   <li>한 줄 안에서 끝나는, 줄 전체가 {@code /* … *}{@code /} 인 줄</li>
 * </ul>
 * 그래서 템플릿의 JS·CSS 주석은 줄 전체 주석으로만 쓴다.
 */
final class TemplateComments {
    /** 줄 전체가 HTML 주석인 줄 (줄바꿈까지). */
    private static final Pattern HTML_LINE = Pattern.compile(
        "(?m)^[ \\t]*<!--(?!\\[if|<!|>)[\\s\\S]*?-->[ \\t]*\\R");
    /** 다른 내용과 같은 줄에 있는 HTML 주석. */
    private static final Pattern HTML_INLINE = Pattern.compile("<!--(?!\\[if|<!|>)[\\s\\S]*?-->");
    private static final Pattern SLASH_LINE = Pattern.compile("(?m)^[ \\t]*//(?:[ \\t].*)?\\R");
    private static final Pattern BLOCK_LINE = Pattern.compile("(?m)^[ \\t]*/\\*.*?\\*/[ \\t]*\\R");

    private TemplateComments() {
    }

    /** [template] 에서 주석을 지운 글. 주석만 있던 줄은 줄째 지운다. */
    static String strip(String template) {
        String text = HTML_LINE.matcher(template).replaceAll("");

        text = HTML_INLINE.matcher(text).replaceAll("");
        text = SLASH_LINE.matcher(text).replaceAll("");

        return BLOCK_LINE.matcher(text).replaceAll("");
    }
}
