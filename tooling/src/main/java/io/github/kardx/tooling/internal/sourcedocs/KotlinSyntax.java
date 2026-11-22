package io.github.kardx.tooling.internal.sourcedocs;

import java.util.Set;

/** Kotlin 선언을 알아보는 키워드와 "여기서 새 선언이 시작하는가" 판단. */
final class KotlinSyntax {
    static final String CLASS = "class";
    static final String INTERFACE = "interface";
    static final String OBJECT = "object";
    static final String FUN = "fun";
    static final String CONSTRUCTOR = "constructor";
    static final String ENUM = "enum";
    static final String COMPANION = "companion";

    /** 선언을 시작하는 키워드. */
    static final Set<String> DECLARATION_KEYWORDS = Set.of(
        "val", "var", FUN, CLASS, INTERFACE, OBJECT, "init", CONSTRUCTOR, "typealias");

    /** 선언 앞의 수식어 (대부분 soft keyword 라 식별자일 수도 있다). */
    static final Set<String> MODIFIERS = Set.of(
        "public", "private", "protected", "internal", "open", "abstract", "final", "sealed", "data", "value",
        "inline", ENUM, "annotation", "inner", COMPANION, "override", "lateinit", "const", "external", "expect",
        "actual", "tailrec", "operator", "infix", "suspend");

    /** 생성자·함수 파라미터 앞의 수식어. */
    static final Set<String> PARAMETER_MODIFIERS = Set.of(
        "val", "var", "vararg", "noinline", "crossinline", "public", "private", "protected", "internal", "override",
        "open", "final");

    /** 앞에 오면 {@code object}·{@code fun} 이 식의 일부인 낱말 ({@code return object : X {}}). */
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of(
        "return", "else", "throw", "in", "is", "as", "to", "by", "when", "if");

    private KotlinSyntax() {
    }

    /**
     * 다음 토큰에서 새 선언이 시작하는가. 수식어는 다음 토큰도 수식어나 선언 키워드일 때만 그렇다 ({@code data} 는 변수
     * 이름일 수 있다).
     */
    static boolean startsDeclaration(TokenCursor cursor) {
        Token token = cursor.peek();

        if (!token.isWord()) {
            return false;
        }

        if (DECLARATION_KEYWORDS.contains(token.text())) {
            return true;
        }

        Token next = cursor.peek(1);

        return MODIFIERS.contains(token.text()) && next.isWord()
            && (MODIFIERS.contains(next.text()) || DECLARATION_KEYWORDS.contains(next.text()));
    }

    /** 식 안의 {@code object : X {}}·익명 함수처럼 앞 토큰이 식을 이어 가는가. */
    static boolean continuesExpression(TokenCursor cursor) {
        Token token = cursor.peek();

        if (!token.isWord(OBJECT) && !token.isWord(FUN)) {
            return false;
        }

        Token previous = cursor.previous();

        if (previous.isWord()) {
            return EXPRESSION_KEYWORDS.contains(previous.text());
        }

        return previous.kind() == TokenKind.SYMBOL && !previous.isCloser();
    }

    /** 이름 자리에 쓸 수 없는 낱말 (선언 키워드·수식어). */
    static boolean isKeyword(String word) {
        return DECLARATION_KEYWORDS.contains(word) || MODIFIERS.contains(word);
    }
}
