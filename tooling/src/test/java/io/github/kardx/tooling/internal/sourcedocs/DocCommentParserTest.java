package io.github.kardx.tooling.internal.sourcedocs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 문서 주석 정리와 첫 문장 ({@link DocCommentParser}, {@link FirstSentence}). */
class DocCommentParserTest {

    @Test
    void blockTagsLeaveTheMainTextAndNamedTagsAreKept() {
        DocComment doc = DocCommentParser.parse("""
            /**
             * 결제를 승인한다. 금액은 1.5 배까지
             * 허용한다.
             *
             * @param amount 결제
             *   금액
             * @property currency 통화
             * @return 승인 결과
             * @throws IllegalStateException 실패
             * @see Payment
             * @sample samples.pay
             */""", SourceLanguage.KOTLIN);

        assertEquals("결제를 승인한다. 금액은 1.5 배까지 허용한다.", doc.description());
        assertEquals("결제를 승인한다", doc.summary());
        assertEquals(Map.of("amount", "결제 금액"), doc.tagged(DocCommentParser.PARAM));
        assertEquals(Map.of("currency", "통화"), doc.tagged(DocCommentParser.PROPERTY));
    }

    @Test
    void summaryStopsAtFirstParagraphWhenThereIsNoPeriod() {
        DocComment doc = DocCommentParser.parse("/** 사용자 목록\n *\n * 페이지로 나눈다. */", SourceLanguage.KOTLIN);

        assertEquals("사용자 목록", doc.summary());
        assertEquals("사용자 목록 페이지로 나눈다.", doc.description());
    }

    @Test
    void singleLineAndEmptyComments() {
        assertEquals("닉네임", DocCommentParser.parse("/** 닉네임 */", SourceLanguage.KOTLIN).description());
        assertEquals("", DocCommentParser.parse("/** */", SourceLanguage.JAVA).description());
        assertEquals("", DocCommentParser.parse("/**\n * @param x 값\n */", SourceLanguage.JAVA).summary());
    }

    @Test
    void javadocInlineTagsWithNestedBracesAndEntities() {
        DocComment doc = DocCommentParser.parse("""
            /**
             * {@code Map<String, {x}>} 를 {@link java.util.Map#get(Object) 읽는다}.<br/>
             * {@link Foo#bar(int, String)} &#64;Bean &#x41; &unknown; {@literal <b>}
             */""", SourceLanguage.JAVA);

        assertEquals("`Map<String, {x}>` 를 읽는다. Foo.bar @Bean A &unknown; <b>", doc.description());
        assertEquals("`Map<String, {x}>` 를 읽는다", doc.summary());
    }

    @Test
    void kotlinKeepsAngleBracketsThatJavadocWouldTreatAsHtml() {
        DocComment doc = DocCommentParser.parse("/** [List]<[String]> 를 돌려준다 */", SourceLanguage.KOTLIN);

        assertEquals("List<String> 를 돌려준다", doc.description());
    }
}
