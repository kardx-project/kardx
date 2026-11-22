package io.github.kardx.tooling.internal.sourcedocs;

import java.util.ArrayList;
import java.util.List;

/**
 * Kotlin·Java 소스를 {@link Token} 목록으로 나눈다. 선언을 찾는 데 필요한 만큼만 본다.
 * <ul>
 *   <li>공백·한 줄 주석·일반 블록 주석은 버린다. Kotlin 블록 주석은 중첩할 수 있다</li>
 *   <li>문서 주석({@code /**})은 원문을 {@link TokenKind#DOC} 로 남긴다</li>
 *   <li>문자열(Kotlin raw string·문자열 템플릿 {@code ${…}}, Java text block 포함)과 문자 리터럴은 내용 없는
 *       {@link TokenKind#LITERAL} 하나가 된다. 그 안의 {@code /**}·괄호는 토큰이 되지 않는다</li>
 *   <li>Kotlin 레이블({@code this@Outer})은 한 낱말이다</li>
 * </ul>
 * 닫히지 않은 문자열·주석은 파일 끝(문자열은 줄 끝)에서 끝난 것으로 본다. 예외를 던지지 않는다.
 */
final class SourceLexer {
    private static final String RAW_QUOTES = "\"\"\"";

    private final String source;
    private final boolean kotlin;
    private final int length;
    private final List<Token> tokens = new ArrayList<>();
    private int pos;

    private SourceLexer(String source, SourceLanguage language) {
        this.source = source;
        this.kotlin = language == SourceLanguage.KOTLIN;
        this.length = source.length();
    }

    static List<Token> tokenize(String source, SourceLanguage language) {
        SourceLexer lexer = new SourceLexer(source, language);

        lexer.run();

        return lexer.tokens;
    }

    private void run() {
        while (pos < length) {
            char c = source.charAt(pos);

            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == '/' && at(pos + 1, '/')) {
                skipLine();
            } else if (c == '/' && at(pos + 1, '*')) {
                comment();
            } else if (c == '"' || c == '\'') {
                literal(c);
                tokens.add(new Token(TokenKind.LITERAL, ""));
            } else if (c == '`') {
                backtickWord();
            } else if (Character.isJavaIdentifierStart(c) || Character.isDigit(c)) {
                word();
            } else {
                symbol();
            }
        }
    }

    private boolean at(int index, char c) {
        return index < length && source.charAt(index) == c;
    }

    private void skipLine() {
        while (pos < length && source.charAt(pos) != '\n') {
            pos++;
        }
    }

    private void comment() {
        int start = pos;
        boolean doc = at(pos + 2, '*') && !at(pos + 3, '/');

        pos += 2;
        skipBlockComment();

        if (doc) {
            tokens.add(new Token(TokenKind.DOC, source.substring(start, pos)));
        }
    }

    /** 여는 {@code /*} 다음부터 닫는 {@code *}{@code /} 까지. Kotlin 은 중첩을 센다. */
    private void skipBlockComment() {
        int depth = 1;

        while (pos < length && depth > 0) {
            if (source.startsWith("*/", pos)) {
                depth--;
                pos += 2;
            } else if (kotlin && source.startsWith("/*", pos)) {
                depth++;
                pos += 2;
            } else {
                pos++;
            }
        }
    }

    private void literal(char quote) {
        if (quote == '\'') {
            charLiteral();
        } else if (source.startsWith(RAW_QUOTES, pos)) {
            rawString();
        } else {
            quotedString();
        }
    }

    private void charLiteral() {
        pos++;

        if (at(pos, '\\')) {
            pos++;
        }

        pos++;

        // '\u0000' 처럼 여러 글자일 수 있다. 줄을 넘기지 않는다
        while (pos < length && source.charAt(pos) != '\'' && source.charAt(pos) != '\n') {
            pos++;
        }

        if (at(pos, '\'')) {
            pos++;
        }
    }

    private void quotedString() {
        pos++;

        while (pos < length) {
            char c = source.charAt(pos);

            if (c == '\\') {
                pos += 2;
            } else if (c == '"') {
                pos++;

                return;
            } else if (c == '\n') {
                return;
            } else if (!template()) {
                pos++;
            }
        }
    }

    /** Kotlin raw string, Java text block. 닫는 따옴표가 셋보다 많으면 앞의 것은 내용이다. */
    private void rawString() {
        pos += RAW_QUOTES.length();

        while (pos < length) {
            if (source.startsWith(RAW_QUOTES, pos)) {
                while (at(pos, '"')) {
                    pos++;
                }

                return;
            }

            if (!kotlin && at(pos, '\\')) {
                pos += 2;
            } else if (!template()) {
                pos++;
            }
        }
    }

    /** Kotlin 문자열 템플릿 {@code ${…}} 이면 닫는 중괄호까지 건너뛰고 true. */
    private boolean template() {
        if (!kotlin || !source.startsWith("${", pos)) {
            return false;
        }

        pos += 2;
        skipTemplateCode();

        return true;
    }

    /** 템플릿 안의 코드. 그 안의 문자열·주석·중괄호를 따라간다. */
    private void skipTemplateCode() {
        int depth = 1;

        while (pos < length) {
            char c = source.charAt(pos);

            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                pos++;

                return;
            } else if (c == '"' || c == '\'') {
                literal(c);

                continue;
            } else if (c == '/' && at(pos + 1, '*')) {
                pos += 2;
                skipBlockComment();

                continue;
            }

            pos++;
        }
    }

    private void backtickWord() {
        int start = ++pos;

        while (pos < length && source.charAt(pos) != '`' && source.charAt(pos) != '\n') {
            pos++;
        }

        tokens.add(new Token(TokenKind.WORD, source.substring(start, pos)));

        if (at(pos, '`')) {
            pos++;
        }
    }

    private void word() {
        int start = pos;

        skipIdentifier();

        // Kotlin 레이블 (this@Outer, return@forEach). 어노테이션은 앞에 식별자가 붙지 않는다
        if (kotlin && at(pos, '@') && pos + 1 < length && Character.isJavaIdentifierStart(source.charAt(pos + 1))) {
            pos++;
            skipIdentifier();
        }

        tokens.add(new Token(TokenKind.WORD, source.substring(start, pos)));
    }

    private void skipIdentifier() {
        pos++;

        while (pos < length && Character.isJavaIdentifierPart(source.charAt(pos))) {
            pos++;
        }
    }

    private void symbol() {
        int size = source.startsWith("->", pos) || source.startsWith("::", pos) ? 2 : 1;

        tokens.add(new Token(TokenKind.SYMBOL, source.substring(pos, pos + size)));
        pos += size;
    }
}
