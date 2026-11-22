package io.github.kardx.tooling.internal.pages;

/** 플러그인이 만드는 HTML 페이지에 넣는 글자. */
final class HtmlText {
    private HtmlText() {
    }

    /** 요소 내용·속성 값에 그대로 넣을 수 있게 {@code & < > "} 를 바꾼다. */
    static String escape(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
