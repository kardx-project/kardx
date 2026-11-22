package io.github.kardx.tooling.internal.sourcedocs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정리한 문서 주석.
 *
 * @param description 블록 태그를 뺀 본문 전체 (한 줄). 없으면 빈 문자열
 * @param summary 본문의 첫 문장 (끝 마침표 없음). 없으면 빈 문자열
 * @param tags 이름이 있는 블록 태그 ({@code @param x}, {@code @property x})
 */
record DocComment(String description, String summary, List<DocTag> tags) {

    /** 이름이 있는 블록 태그 하나. {@code name} 은 {@code @} 를 뺀 태그 이름이다. */
    record DocTag(String name, String subject, String text) {
    }

    boolean hasText() {
        return !description.isEmpty();
    }

    /** {@code @<tagName> <subject> <text>} 의 subject → text (처음 것). 글이 빈 태그는 뺀다. */
    Map<String, String> tagged(String tagName) {
        Map<String, String> texts = new LinkedHashMap<>();

        for (DocTag tag : tags) {
            if (tag.name().equals(tagName) && !tag.text().isEmpty()) {
                texts.putIfAbsent(tag.subject(), tag.text());
            }
        }

        return texts;
    }
}
