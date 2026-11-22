package io.github.kardx.tooling.internal.sourcedocs;

/**
 * enum 본문 앞의 상수 목록 ({@code A, B("x"), C { … };}). 두 언어가 같은 꼴이다. 상수 뒤에 {@code , ; ( { }} 가 오지
 * 않으면 상수 목록이 끝난 것으로 본다. 끝의 {@code ;} 는 읽고, 본문을 닫는 {@code }} 는 남긴다.
 */
final class EnumConstants {
    private EnumConstants() {
    }

    static void parse(TokenCursor cursor, TypeDocs type, SourceLanguage language) {
        Token doc = null;

        while (!cursor.atEnd()) {
            Token token = cursor.peek();

            if (token.isDoc()) {
                doc = cursor.next();
            } else if (token.isSymbol("@")) {
                cursor.skipAnnotation();
            } else if (isConstant(cursor)) {
                String name = cursor.next().text();

                if (doc != null) {
                    type.enumConstant(name, DocCommentParser.parse(doc.text(), language).description());
                }

                doc = null;
                skipArgumentsAndBody(cursor);

                if (!cursor.accept(",")) {
                    cursor.accept(";");

                    return;
                }
            } else {
                cursor.accept(";");

                return;
            }
        }
    }

    private static boolean isConstant(TokenCursor cursor) {
        Token next = cursor.peek(1);

        return cursor.peek().isWord() && (next.isSymbol(",") || next.isSymbol(";") || next.isSymbol("(")
            || next.isSymbol("{") || next.isSymbol("}"));
    }

    private static void skipArgumentsAndBody(TokenCursor cursor) {
        if (cursor.at("(")) {
            cursor.skipBalanced();
        }

        if (cursor.at("{")) {
            cursor.skipBalanced();
        }
    }
}
