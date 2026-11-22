package io.github.kardx.tooling.internal.sourcedocs;

/**
 * 파일의 패키지를 읽고 타입의 JVM binary name 을 만든다 ({@code com.example.Outer$Inner}). 파일 머리
 * ({@code package}, {@code import}, Kotlin {@code @file:} 어노테이션, 그 앞의 문서 주석)를 건너뛴다.
 */
final class JvmNames {
    private String packagePrefix = "";

    /** 첫 선언 앞에서 멈춘다. 첫 선언 앞의 문서 주석·어노테이션은 남긴다. */
    void prelude(TokenCursor cursor, boolean kotlin) {
        while (!cursor.atEnd()) {
            int docs = docsAhead(cursor);
            Token token = cursor.peek(docs);

            if (token.isWord("package")) {
                cursor.reset(cursor.mark() + docs + 1);
                String name = qualifiedName(cursor);

                packagePrefix = name.isEmpty() ? "" : name + ".";
            } else if (token.isWord("import")) {
                cursor.reset(cursor.mark() + docs + 1);
                skipImport(cursor);
            } else if (kotlin && docs == 0 && isFileAnnotation(cursor)) {
                cursor.skipAnnotation();
            } else {
                return;
            }

            cursor.accept(";");
        }
    }

    /** @param owner 바깥 타입의 binary name, 최상위면 null */
    String binary(String owner, String name) {
        return owner == null ? packagePrefix + name : owner + "$" + name;
    }

    private static int docsAhead(TokenCursor cursor) {
        int count = 0;

        while (cursor.peek(count).isDoc()) {
            count++;
        }

        return count;
    }

    private static boolean isFileAnnotation(TokenCursor cursor) {
        return cursor.at("@") && cursor.peek(1).isWord("file") && cursor.peek(2).isSymbol(":");
    }

    private static void skipImport(TokenCursor cursor) {
        cursor.acceptWord("static");
        qualifiedName(cursor);

        if (cursor.acceptWord("as")) {
            cursor.word();
        }
    }

    /** {@code a.b.C}, {@code a.b.*}. */
    private static String qualifiedName(TokenCursor cursor) {
        StringBuilder name = new StringBuilder();
        String word = cursor.word();

        if (word != null) {
            name.append(word);
        }

        while (cursor.at(".") && (cursor.peek(1).isWord() || cursor.peek(1).isSymbol("*"))) {
            cursor.next();
            name.append('.').append(cursor.next().text());
        }

        return name.toString();
    }
}
