package io.github.kardx.tooling.internal.sourcedocs;

/**
 * Kotlin 선언에서 문서와 상관없는 부분(타입, 식, 클래스 머리의 상위 타입, 함수 본문)을 건너뛴다. 세미콜론이 없으므로 식의
 * 끝은 "깊이 0 에서 새 선언이 시작하는 자리"로 정한다 ({@link KotlinSyntax#startsDeclaration}).
 */
final class KotlinSkipper {
    private final TokenCursor cursor;

    KotlinSkipper(TokenCursor cursor) {
        this.cursor = cursor;
    }

    /** {@code Map<String, (Int) -> Unit>?}, {@code suspend Foo.() -> Unit}, {@code @A Type}. */
    void type() {
        while (cursor.at("@")) {
            cursor.skipAnnotation();
        }

        cursor.acceptWord("suspend");

        if (cursor.at("(")) {
            cursor.skipBalanced();
        } else if (!namedType()) {
            return;
        }

        while (cursor.accept("?")) {
            // 널 가능 표시는 몇 개든 건너뛴다
        }

        // 수신 객체가 있는 함수 타입 (Foo.() -> Unit)
        if (cursor.at(".") && cursor.peek(1).isSymbol("(")) {
            cursor.next();
            cursor.skipBalanced();
        }

        if (cursor.accept("->")) {
            type();
        }
    }

    /** {@code a.b.C<T>.D<U>}. 이름이 없으면 false. */
    private boolean namedType() {
        if (cursor.word() == null && !cursor.accept("*")) {
            return false;
        }

        while (true) {
            if (cursor.at("<")) {
                cursor.skipAngle();
            } else if (cursor.at(".") && cursor.peek(1).isWord()) {
                cursor.next();
                cursor.next();
            } else {
                return true;
            }
        }
    }

    /** 초기값·getter·setter·위임·식 본문. 깊이 0 에서 새 선언, 닫는 괄호, 문서 주석, 어노테이션, {@code ;} 에서 멈춘다. */
    void expression() {
        while (!cursor.atEnd()) {
            Token token = cursor.peek();

            if (token.isOpener()) {
                cursor.skipBalanced();

                continue;
            }

            if (token.isSymbol(";")) {
                cursor.next();

                return;
            }

            if (endsExpression(token)) {
                return;
            }

            if (token.isSymbol("<") && cursor.previous().isWord() && cursor.skipAngle()) {
                // 타입 인자를 쓴 호출 (emptyMap<String, Int>())
                continue;
            }

            cursor.next();
        }
    }

    private boolean endsExpression(Token token) {
        if (token.isCloser() || token.isDoc() || token.isSymbol("@")) {
            return true;
        }

        return KotlinSyntax.startsDeclaration(cursor) && !KotlinSyntax.continuesExpression(cursor);
    }

    /**
     * 클래스·object 머리의 나머지 (상위 타입, 위임, {@code where}). 본문 {@code {} 앞에서 멈추고 true, 본문 없이 끝나면 false.
     */
    boolean header() {
        while (!cursor.atEnd()) {
            Token token = cursor.peek();

            if (token.isSymbol("{")) {
                return true;
            }

            if (token.isCloser() || token.isDoc() || token.isSymbol("@") || token.isSymbol(";")
                || KotlinSyntax.startsDeclaration(cursor)) {
                return false;
            }

            if (!token.isSymbol("<") || !cursor.skipAngle()) {
                cursor.skipOne();
            }
        }

        return false;
    }

    /** 함수의 반환 타입 뒤: {@code where} 제약, 블록 본문 또는 식 본문. */
    void functionTail() {
        if (cursor.acceptWord("where")) {
            while (!cursor.atEnd() && !cursor.at("{") && !cursor.at("=") && !cursor.peek().isCloser()
                && !KotlinSyntax.startsDeclaration(cursor)) {
                cursor.skipOne();
            }
        }

        if (cursor.at("{")) {
            cursor.skipBalanced();
        } else if (cursor.accept("=")) {
            expression();
        }
    }

    /** 생성자·함수 파라미터 하나의 기본값. 깊이 0 의 {@code ,} 또는 {@code )} 앞에서 멈춘다. */
    void parameterRest() {
        while (!cursor.atEnd() && !cursor.at(",") && !cursor.at(")")) {
            if (cursor.at("<") && cursor.previous().isWord() && cursor.skipAngle()) {
                continue;
            }

            cursor.skipOne();
        }
    }
}
