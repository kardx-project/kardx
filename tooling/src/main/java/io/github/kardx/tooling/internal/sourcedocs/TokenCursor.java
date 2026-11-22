package io.github.kardx.tooling.internal.sourcedocs;

import java.util.List;

/**
 * 토큰 목록 위를 움직이며 두 언어에 공통인 구조(괄호 짝, 타입 인자, 어노테이션)를 건너뛴다. 끝을 넘어 읽으면
 * {@link Token#EOF} 를 준다. 짝이 맞지 않으면 끝에서 멈출 뿐 예외를 던지지 않는다.
 */
final class TokenCursor {
    private final List<Token> tokens;
    private int index;

    TokenCursor(List<Token> tokens) {
        this.tokens = tokens;
    }

    boolean atEnd() {
        return index >= tokens.size();
    }

    Token peek() {
        return peek(0);
    }

    Token peek(int ahead) {
        int at = index + ahead;

        return at < tokens.size() ? tokens.get(at) : Token.EOF;
    }

    /** 바로 앞에 읽은 토큰. 처음이면 {@link Token#EOF}. */
    Token previous() {
        return index > 0 ? tokens.get(index - 1) : Token.EOF;
    }

    Token next() {
        Token token = peek();

        if (!atEnd()) {
            index++;
        }

        return token;
    }

    int mark() {
        return index;
    }

    void reset(int mark) {
        index = mark;
    }

    /** 다음 토큰이 이 문장 부호인가. */
    boolean at(String symbol) {
        return peek().isSymbol(symbol);
    }

    /** 다음 토큰이 이 문장 부호이면 읽고 true. */
    boolean accept(String symbol) {
        if (at(symbol)) {
            index++;

            return true;
        }

        return false;
    }

    /** 다음 토큰이 이 낱말이면 읽고 true. */
    boolean acceptWord(String word) {
        if (peek().isWord(word)) {
            index++;

            return true;
        }

        return false;
    }

    /** 다음 토큰이 낱말이면 읽어 준다. 아니면 null 이고 움직이지 않는다. */
    String word() {
        return peek().isWord() ? next().text() : null;
    }

    /** 여는 괄호에서 짝이 되는 닫는 괄호 다음까지. 여는 괄호가 아니면 한 토큰만 읽는다. */
    void skipBalanced() {
        Token open = next();

        if (!open.isOpener()) {
            return;
        }

        String close = closing(open.text());
        int depth = 1;

        while (!atEnd() && depth > 0) {
            Token token = next();

            if (token.isSymbol(open.text())) {
                depth++;
            } else if (token.isSymbol(close)) {
                depth--;
            }
        }
    }

    /**
     * {@code <} 에서 짝이 되는 {@code >} 다음까지 (중첩, {@code >>} 포함). 타입 인자가 아닌 것(비교식)을 만나면 처음
     * 자리로 돌아가 false.
     */
    boolean skipAngle() {
        int start = index;
        int depth = 0;

        while (!atEnd()) {
            Token token = peek();

            if (token.isSymbol("<")) {
                depth++;
            } else if (token.isSymbol(">") && --depth == 0) {
                index++;

                return true;
            } else if (token.isSymbol("(") || token.isSymbol("[")) {
                skipBalanced();

                continue;
            } else if (endsTypeArguments(token)) {
                break;
            }

            index++;
        }

        index = start;

        return false;
    }

    private static boolean endsTypeArguments(Token token) {
        return token.isDoc() || token.kind() == TokenKind.LITERAL || token.isCloser() || token.isSymbol("{")
            || token.isSymbol(";") || token.isSymbol("=");
    }

    /**
     * {@code @} 에서 어노테이션 끝까지: 이름({@code a.b.C}, Kotlin use-site target {@code field:C}), 인자 괄호(람다·배열
     * 포함), Kotlin 의 {@code @[A B]}.
     */
    void skipAnnotation() {
        next();

        if (at("[")) {
            skipBalanced();

            return;
        }

        word();

        while ((at(".") || at(":")) && peek(1).isWord()) {
            index += 2;
        }

        if (at("(")) {
            skipBalanced();
        }
    }

    /** 모르는 자리의 토큰 하나 (괄호면 짝까지). */
    void skipOne() {
        if (peek().isOpener()) {
            skipBalanced();
        } else {
            next();
        }
    }

    private static String closing(String open) {
        return switch (open) {
            case "(" -> ")";
            case "[" -> "]";
            default -> "}";
        };
    }
}
