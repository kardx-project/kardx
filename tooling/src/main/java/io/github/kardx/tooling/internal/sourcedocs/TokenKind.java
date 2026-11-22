package io.github.kardx.tooling.internal.sourcedocs;

/** {@link SourceLexer} 가 만드는 토큰 종류. */
enum TokenKind {
    /** 식별자·키워드·숫자. Kotlin 의 {@code `이름`} 은 백틱을 뺀 이름이다. */
    WORD,
    /** 문장 부호 한 글자. {@code ->}, {@code ::} 만 두 글자다. */
    SYMBOL,
    /** {@code /** … *}{@code /} 문서 주석 원문. */
    DOC,
    /** 문자열·문자 리터럴. 내용은 버린다. */
    LITERAL,
    /** 끝. */
    EOF
}
