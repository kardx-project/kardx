package io.github.kardx.tooling.internal.sourcedocs;

/**
 * 문단의 첫 문장: 처음 나오는 "마침표 + 공백" 또는 끝의 마침표까지, 마침표는 뺀다 ({@code 사용자를 조회한다. 탈퇴한 …} →
 * {@code 사용자를 조회한다}). 마침표가 없으면 문단 전체다. {@code 1.5} 처럼 바로 글자가 이어지는 마침표는 문장 끝이 아니다.
 */
final class FirstSentence {
    private FirstSentence() {
    }

    static String of(String paragraph) {
        for (int i = 0; i < paragraph.length(); i++) {
            boolean end = i + 1 == paragraph.length() || Character.isWhitespace(paragraph.charAt(i + 1));

            if (paragraph.charAt(i) == '.' && end) {
                return paragraph.substring(0, i).strip();
            }
        }

        return paragraph;
    }
}
