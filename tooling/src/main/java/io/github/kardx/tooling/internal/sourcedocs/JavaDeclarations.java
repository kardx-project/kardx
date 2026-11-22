package io.github.kardx.tooling.internal.sourcedocs;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Java 파일 하나의 선언에서 문서를 모은다: class·interface·enum·record·{@code @interface}, 필드, getter
 * ({@code getX}·{@code isX})와 record 접근자, record 컴포넌트, enum 상수, 메서드. 메서드·초기화 블록 본문은 건너뛴다.
 * 모르는 꼴은 {@code ;} 또는 블록까지 건너뛰므로 그 선언만 빠진다.
 */
final class JavaDeclarations {
    private static final String RECORD = "record";
    private static final Set<String> TYPE_KEYWORDS = Set.of("class", "interface", "enum", RECORD);
    private static final Set<String> MODIFIERS = Set.of("public", "private", "protected", "static", "final",
        "abstract", "default", "synchronized", "native", "transient", "volatile", "strictfp", "sealed");

    private final TokenCursor cursor;
    private final SourceDocsIndex index;
    private final JvmNames names = new JvmNames();

    private JavaDeclarations(List<Token> tokens, SourceDocsIndex index) {
        this.cursor = new TokenCursor(tokens);
        this.index = index;
    }

    static void parse(List<Token> tokens, SourceDocsIndex index) {
        new JavaDeclarations(tokens, index).file();
    }

    private void file() {
        names.prelude(cursor, false);

        while (!cursor.atEnd()) {
            members(null, Set.of());
        }
    }

    /** @param components record 컴포넌트 이름 (접근자 문서를 알아보는 데 쓴다) */
    private void members(String owner, Set<String> components) {
        Token doc = null;

        while (!cursor.atEnd()) {
            Token token = cursor.peek();

            if (cursor.accept("}")) {
                return;
            }

            if (token.isDoc()) {
                doc = cursor.next();
            } else if (token.isSymbol("@") && !cursor.peek(1).isWord("interface")) {
                cursor.skipAnnotation();
            } else if (!skipModifier()) {
                declaration(owner, doc, components);
                doc = null;
            }
        }
    }

    private boolean skipModifier() {
        Token token = cursor.peek();

        if (token.isWord() && MODIFIERS.contains(token.text())) {
            cursor.next();

            return true;
        }

        // non-sealed
        if (token.isWord("non") && cursor.peek(1).isSymbol("-") && cursor.peek(2).isWord("sealed")) {
            cursor.reset(cursor.mark() + 3);

            return true;
        }

        return false;
    }

    private void declaration(String owner, Token doc, Set<String> components) {
        // 빈 선언
        if (cursor.accept(";")) {
            return;
        }

        if (cursor.accept("@")) {
            typeDeclaration(owner, doc);
        } else if (cursor.at("{")) {
            cursor.skipBalanced();
        } else if (isTypeDeclaration()) {
            typeDeclaration(owner, doc);
        } else {
            member(owner, doc, components);
        }
    }

    /** {@code record} 는 식별자일 수 있으므로 {@code record Name(} 꼴일 때만 타입이다. */
    private boolean isTypeDeclaration() {
        Token token = cursor.peek();

        if (!token.isWord() || !TYPE_KEYWORDS.contains(token.text()) || !cursor.peek(1).isWord()) {
            return false;
        }

        return !token.isWord(RECORD) || cursor.peek(2).isSymbol("(") || cursor.peek(2).isSymbol("<");
    }

    private void typeDeclaration(String owner, Token doc) {
        String kind = cursor.next().text();
        String name = cursor.word();

        if (name == null) {
            return;
        }

        String binary = names.binary(owner, name);
        TypeDocs type = index.type(binary);
        boolean record = RECORD.equals(kind);

        if (doc != null) {
            type.document(doc(doc), record);
        }

        if (cursor.at("<")) {
            cursor.skipAngle();
        }

        Set<String> components = record && cursor.at("(") ? recordComponents(type) : Set.of();

        // extends, implements, permits
        while (!cursor.atEnd() && !cursor.at("{") && !cursor.at(";") && !cursor.peek().isCloser()) {
            cursor.skipOne();
        }

        if (cursor.accept("{")) {
            if ("enum".equals(kind)) {
                EnumConstants.parse(cursor, type, SourceLanguage.JAVA);
            }

            members(binary, components);
        }
    }

    private Set<String> recordComponents(TypeDocs type) {
        Set<String> components = new LinkedHashSet<>();

        cursor.next();

        while (!cursor.atEnd() && !cursor.accept(")")) {
            Token doc = cursor.peek().isDoc() ? cursor.next() : null;
            String name = type() ? cursor.word() : null;

            if (name != null) {
                components.add(name);

                if (doc != null) {
                    type.property(name, doc(doc).description(), DocPrecedence.DECLARATION);
                }
            }

            while (!cursor.atEnd() && !cursor.at(",") && !cursor.at(")")) {
                cursor.skipOne();
            }

            cursor.accept(",");
        }

        return components;
    }

    /** 생성자, 메서드, 필드. */
    private void member(String owner, Token doc, Set<String> components) {
        if (cursor.at("<")) {
            cursor.skipAngle();
        }

        if (cursor.peek().isWord() && cursor.peek(1).isSymbol("(")) {
            cursor.next();
            parameters();
            skipMethodRest();

            return;
        }

        String name = type() ? cursor.word() : null;

        if (name == null) {
            skipMethodRest();
        } else if (cursor.at("(")) {
            method(owner, name, doc, components);
        } else {
            fields(owner, name, doc);
        }
    }

    private void method(String owner, String name, Token doc, Set<String> components) {
        int arity = parameters();
        DocComment parsed = doc == null ? null : doc(doc);

        if (owner != null) {
            index.method(owner, name, arity, parsed);

            String property = arity == 0 && parsed != null ? AccessorNames.property(name, components) : null;

            if (property != null) {
                index.type(owner).property(property, parsed.description(), DocPrecedence.ACCESSOR);
            }
        }

        skipMethodRest();
    }

    /** {@code int a = 1, b[];}. Javadoc 은 한 선언의 모든 필드에 붙는다. */
    private void fields(String owner, String first, Token doc) {
        String text = doc == null || owner == null ? null : doc(doc).description();
        String name = first;

        while (name != null) {
            if (text != null) {
                index.type(owner).property(name, text, DocPrecedence.DECLARATION);
            }

            while (cursor.at("[")) {
                cursor.skipBalanced();
            }

            if (cursor.accept("=")) {
                initializer();
            }

            name = cursor.accept(",") ? cursor.word() : null;
        }

        cursor.accept(";");
    }

    private void initializer() {
        while (!cursor.atEnd() && !cursor.at(",") && !cursor.at(";") && !cursor.peek().isCloser()) {
            if (!(cursor.at("<") && cursor.previous().isWord() && cursor.skipAngle())) {
                cursor.skipOne();
            }
        }
    }

    /** 타입 ({@code @A java.util.Map<K, V>[]}, {@code String...}). 이름이 없으면 false. */
    private boolean type() {
        while (cursor.at("@")) {
            cursor.skipAnnotation();
        }

        if (cursor.word() == null) {
            return false;
        }

        while (true) {
            if (cursor.at("<")) {
                if (!cursor.skipAngle()) {
                    return false;
                }
            } else if (cursor.at(".") && cursor.peek(1).isWord()) {
                cursor.reset(cursor.mark() + 2);
            } else if (cursor.at("[") && cursor.peek(1).isSymbol("]")
                || cursor.at(".") && cursor.peek(1).isSymbol(".")) {
                cursor.reset(cursor.mark() + 2);
                cursor.accept(".");
            } else {
                return true;
            }
        }
    }

    /** {@code (} 에서 {@code )} 다음까지. @return 깊이 0 의 쉼표로 센 파라미터 수 */
    private int parameters() {
        cursor.next();
        int depth = 0;
        int commas = 0;
        boolean any = false;

        while (!cursor.atEnd()) {
            Token token = cursor.next();

            if (token.isSymbol(")") && depth == 0) {
                break;
            }

            if (token.isOpener() || token.isSymbol("<")) {
                depth++;
            } else if (token.isCloser() || token.isSymbol(">")) {
                depth--;
            } else if (token.isSymbol(",") && depth == 0) {
                commas++;
            }

            any = true;
        }

        return any ? commas + 1 : 0;
    }

    /** {@code throws …}, 기본값({@code default …}), 본문 {@code {…}} 또는 {@code ;}. */
    private void skipMethodRest() {
        while (!cursor.atEnd() && !cursor.peek().isCloser()) {
            if (cursor.at("{")) {
                cursor.skipBalanced();

                return;
            }

            if (cursor.accept(";")) {
                return;
            }

            cursor.skipOne();
        }
    }

    private static DocComment doc(Token token) {
        return DocCommentParser.parse(token.text(), SourceLanguage.JAVA);
    }
}
