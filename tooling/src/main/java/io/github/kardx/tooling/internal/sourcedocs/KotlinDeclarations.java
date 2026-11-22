package io.github.kardx.tooling.internal.sourcedocs;

import java.util.List;

/**
 * Kotlin 파일 하나의 선언에서 문서를 모은다: 클래스·인터페이스·object(companion 은 {@code Outer$Companion}), 주 생성자
 * 파라미터, 본문 속성, enum 상수, 메서드. 함수 본문·초기값·상위 타입은 건너뛴다 ({@link KotlinSkipper}). 모르는 꼴은 토큰
 * 하나씩 건너뛰므로 그 선언만 빠진다. 최상위 함수·속성은 클래스가 없으므로 모으지 않는다.
 */
final class KotlinDeclarations {
    private static final String COMPANION_NAME = "Companion";

    private final TokenCursor cursor;
    private final KotlinSkipper skip;
    private final SourceDocsIndex index;
    private final JvmNames names = new JvmNames();

    private KotlinDeclarations(List<Token> tokens, SourceDocsIndex index) {
        this.cursor = new TokenCursor(tokens);
        this.skip = new KotlinSkipper(cursor);
        this.index = index;
    }

    static void parse(List<Token> tokens, SourceDocsIndex index) {
        new KotlinDeclarations(tokens, index).file();
    }

    private void file() {
        names.prelude(cursor, true);

        while (!cursor.atEnd()) {
            members(null);
        }
    }

    /** 본문을 닫는 {@code }} 까지 (최상위면 파일 끝까지). */
    private void members(String owner) {
        Prefix prefix = new Prefix();

        while (!cursor.atEnd()) {
            if (cursor.accept("}")) {
                return;
            }

            if (!prefix.consume(cursor)) {
                declaration(owner, prefix);
                prefix = new Prefix();
            }
        }
    }

    private void declaration(String owner, Prefix prefix) {
        Token token = cursor.peek();

        switch (token.isWord() ? token.text() : "") {
            case KotlinSyntax.CLASS, KotlinSyntax.INTERFACE -> typeDeclaration(owner, prefix);
            case KotlinSyntax.OBJECT -> objectDeclaration(owner, prefix);
            case KotlinSyntax.FUN -> function(owner, prefix);
            case "val", "var" -> property(owner, prefix);
            case KotlinSyntax.CONSTRUCTOR -> secondaryConstructor(owner, prefix);
            case "init" -> {
                cursor.next();
                skip.functionTail();
            }
            case "typealias" -> {
                cursor.next();
                skip.expression();
            }
            default -> cursor.skipOne();
        }
    }

    private void typeDeclaration(String owner, Prefix prefix) {
        cursor.next();
        String name = cursor.word();

        if (name == null) {
            return;
        }

        String binary = names.binary(owner, name);
        TypeDocs type = index.type(binary);

        if (prefix.doc != null) {
            type.document(doc(prefix.doc), true);
        }

        if (cursor.at("<")) {
            cursor.skipAngle();
        }

        primaryConstructor(type);
        body(binary, prefix.enumClass);
    }

    private void body(String binary, boolean enumClass) {
        if (!skip.header()) {
            return;
        }

        cursor.next();

        if (enumClass) {
            EnumConstants.parse(cursor, index.type(binary), SourceLanguage.KOTLIN);
        }

        members(binary);
    }

    /** {@code [/** 문서 *}{@code /] [@A] [private] [constructor] (파라미터)}. 아니면 읽은 자리를 되돌린다. */
    private void primaryConstructor(TypeDocs type) {
        int mark = cursor.mark();
        Token doc = null;

        while (true) {
            Token token = cursor.peek();

            if (token.isDoc()) {
                doc = cursor.next();
            } else if (token.isSymbol("@")) {
                cursor.skipAnnotation();
            } else if (isVisibility(token)) {
                cursor.next();
            } else {
                break;
            }
        }

        boolean keyword = cursor.acceptWord(KotlinSyntax.CONSTRUCTOR);

        if (!cursor.at("(") || !keyword && cursor.mark() != mark) {
            cursor.reset(mark);

            return;
        }

        if (doc != null) {
            type.parameterTags(doc(doc));
        }

        parameters(type);
    }

    private static boolean isVisibility(Token token) {
        return token.isWord("private") || token.isWord("public") || token.isWord("protected")
            || token.isWord("internal");
    }

    /** {@code (} 에서 {@code )} 다음까지. @param type 주 생성자면 파라미터 문서를 넣을 타입, 아니면 null. @return 파라미터 수 */
    private int parameters(TypeDocs type) {
        cursor.next();
        int count = 0;

        while (!cursor.atEnd() && !cursor.accept(")")) {
            parameter(type);
            count++;
            cursor.accept(",");
        }

        return count;
    }

    private void parameter(TypeDocs type) {
        Token doc = null;

        while (true) {
            Token token = cursor.peek();

            if (token.isDoc()) {
                doc = cursor.next();
            } else if (token.isSymbol("@")) {
                cursor.skipAnnotation();
            } else if (token.isWord() && KotlinSyntax.PARAMETER_MODIFIERS.contains(token.text())
                && cursor.peek(1).isWord()) {
                cursor.next();
            } else {
                break;
            }
        }

        String name = cursor.word();

        if (name != null && cursor.accept(":")) {
            skip.type();
        }

        skip.parameterRest();

        if (type != null && name != null && doc != null) {
            type.property(name, doc(doc).description(), DocPrecedence.DECLARATION);
        }
    }

    private void function(String owner, Prefix prefix) {
        cursor.next();

        if (cursor.peek().isWord(KotlinSyntax.INTERFACE)) {
            typeDeclaration(owner, prefix);

            return;
        }

        if (cursor.at("<")) {
            cursor.skipAngle();
        }

        String name = functionName();

        if (name == null) {
            skip.expression();

            return;
        }

        int arity = parameters(null);

        if (owner != null) {
            index.method(owner, name, arity, prefix.doc == null ? null : doc(prefix.doc));
        }

        if (cursor.accept(":")) {
            skip.type();
        }

        skip.functionTail();
    }

    /** 수신 타입 뒤의 이름 ({@code fun <T> List<T>.second(}). {@code (} 앞에서 멈춘다. */
    private String functionName() {
        String name = null;

        while (!cursor.atEnd() && !cursor.at("(")) {
            String word = cursor.word();

            if (word != null) {
                name = word;
            } else if (!(cursor.at("<") && cursor.skipAngle()) && !cursor.accept(".") && !cursor.accept("?")) {
                return null;
            }
        }

        return name;
    }

    private void property(String owner, Prefix prefix) {
        cursor.next();

        if (cursor.at("<")) {
            cursor.skipAngle();
        }

        String name = propertyName();

        if (owner != null && name != null && prefix.doc != null) {
            index.type(owner).property(name, doc(prefix.doc).description(), DocPrecedence.DECLARATION);
        }

        if (cursor.accept(":")) {
            skip.type();
        }

        skip.expression();
    }

    /** 속성 이름. 확장 속성({@code val String.size})·구조 분해면 null. */
    private String propertyName() {
        if (cursor.at("(")) {
            cursor.skipBalanced();

            return null;
        }

        String name = cursor.word();
        boolean extension = false;

        while (name != null && (cursor.at(".") || cursor.at("?") || cursor.at("<"))) {
            extension = true;

            if (!(cursor.at("<") && cursor.skipAngle())) {
                cursor.next();
            }

            cursor.word();
        }

        return extension ? null : name;
    }

    private void objectDeclaration(String owner, Prefix prefix) {
        cursor.next();
        Token token = cursor.peek();
        String name = token.isWord() && !KotlinSyntax.isKeyword(token.text()) ? cursor.next().text() : null;

        if (name == null && !prefix.companion) {
            // 식으로 쓴 object (object : Foo {}). 문서를 모을 이름이 없다
            if (skip.header()) {
                cursor.skipBalanced();
            }

            return;
        }

        String binary = names.binary(owner, name == null ? COMPANION_NAME : name);

        if (prefix.doc != null) {
            index.type(binary).document(doc(prefix.doc), false);
        }

        body(binary, false);
    }

    private void secondaryConstructor(String owner, Prefix prefix) {
        cursor.next();

        if (owner != null && prefix.doc != null) {
            index.type(owner).parameterTags(doc(prefix.doc));
        }

        if (cursor.at("(")) {
            parameters(null);
        }

        if (cursor.accept(":")) {
            cursor.word();

            if (cursor.at("(")) {
                cursor.skipBalanced();
            }
        }

        if (cursor.at("{")) {
            cursor.skipBalanced();
        }
    }

    private static DocComment doc(Token token) {
        return DocCommentParser.parse(token.text(), SourceLanguage.KOTLIN);
    }

    /** 선언 앞의 문서 주석·어노테이션·수식어. */
    private static final class Prefix {
        private Token doc;
        private boolean enumClass;
        private boolean companion;

        /** 접두 토큰 하나를 읽었으면 true. */
        private boolean consume(TokenCursor cursor) {
            Token token = cursor.peek();

            if (token.isDoc()) {
                doc = cursor.next();
            } else if (token.isSymbol("@")) {
                cursor.skipAnnotation();
            } else if (token.isWord() && KotlinSyntax.MODIFIERS.contains(token.text())) {
                enumClass |= token.isWord(KotlinSyntax.ENUM);
                companion |= token.isWord(KotlinSyntax.COMPANION);
                cursor.next();
            } else {
                return false;
            }

            return true;
        }
    }
}
