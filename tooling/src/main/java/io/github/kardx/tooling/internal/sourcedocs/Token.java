package io.github.kardx.tooling.internal.sourcedocs;

/** 소스 토큰 하나. 리터럴은 내용 없이 종류만 남긴다. */
record Token(TokenKind kind, String text) {
    static final Token EOF = new Token(TokenKind.EOF, "");

    /** 같은 글자의 문장 부호인가. */
    boolean isSymbol(String symbol) {
        return kind == TokenKind.SYMBOL && text.equals(symbol);
    }

    /** 같은 글자의 식별자·키워드인가. */
    boolean isWord(String word) {
        return kind == TokenKind.WORD && text.equals(word);
    }

    boolean isWord() {
        return kind == TokenKind.WORD;
    }

    boolean isDoc() {
        return kind == TokenKind.DOC;
    }

    /** 여는 괄호 ({@code ( [ {}). */
    boolean isOpener() {
        return kind == TokenKind.SYMBOL && ("(".equals(text) || "[".equals(text) || "{".equals(text));
    }

    /** 닫는 괄호 ({@code ) ] }}). */
    boolean isCloser() {
        return kind == TokenKind.SYMBOL && (")".equals(text) || "]".equals(text) || "}".equals(text));
    }
}
