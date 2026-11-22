package io.github.kardx.tooling.internal.sourcedocs;

import io.github.kardx.tooling.internal.sourcedocs.DocComment.DocTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서 주석 원문({@code /** … *}{@code /})을 {@link DocComment} 로 바꾼다. 주석 기호와 줄 앞 {@code *} 를 빼고, 줄 머리의
 * 블록 태그({@code @param}, {@code @return}, {@code @property} …)부터는 본문에서 뺀다. 이름을 받는 태그
 * ({@value #PARAM}, {@value #PROPERTY})는 따로 남긴다.
 */
final class DocCommentParser {
    static final String PARAM = "param";
    static final String PROPERTY = "property";

    private static final Set<String> NAMED_TAGS = Set.of(PARAM, PROPERTY);
    private static final Pattern BLOCK_TAG = Pattern.compile("^@([A-Za-z]+)(?:\\s+(.*))?$", Pattern.DOTALL);
    private static final Pattern SUBJECT = Pattern.compile("^(\\S+)\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern LEADING_STAR = Pattern.compile("^\\s*\\*(?!/)");

    private DocCommentParser() {
    }

    static DocComment parse(String raw, SourceLanguage language) {
        Block main = new Block("");
        List<Block> blocks = new ArrayList<>();
        Block current = main;

        for (String line : lines(raw)) {
            Matcher tag = BLOCK_TAG.matcher(line.strip());

            if (tag.matches()) {
                current = new Block(tag.group(1));
                current.append(tag.group(2));
                blocks.add(current);
            } else {
                current.append(line);
            }
        }

        List<String> paragraphs = DocTextNormalizer.paragraphs(main.body.toString(), language);
        String description = String.join(" ", paragraphs);
        String summary = paragraphs.isEmpty() ? "" : FirstSentence.of(paragraphs.get(0));

        return new DocComment(description, summary, tags(blocks, language));
    }

    private static List<DocTag> tags(List<Block> blocks, SourceLanguage language) {
        List<DocTag> tags = new ArrayList<>();

        for (Block block : blocks) {
            Matcher subject = SUBJECT.matcher(block.body.toString().strip());

            if (NAMED_TAGS.contains(block.name) && subject.matches()) {
                String text = String.join(" ", DocTextNormalizer.paragraphs(subject.group(2), language));

                tags.add(new DocTag(block.name, subject.group(1), text));
            }
        }

        return tags;
    }

    /** 주석 기호와 줄 앞 공백·{@code *} 를 뺀 줄들. */
    private static List<String> lines(String raw) {
        String body = raw.startsWith("/**") ? raw.substring(3) : raw;

        if (body.endsWith("*/")) {
            body = body.substring(0, body.length() - 2);
        }

        List<String> lines = new ArrayList<>();

        for (String line : body.split("\r?\n|\r", -1)) {
            lines.add(LEADING_STAR.matcher(line).replaceFirst(""));
        }

        return lines;
    }

    /** 본문 또는 블록 태그 하나와 그 줄들. */
    private static final class Block {
        private final String name;
        private final StringBuilder body = new StringBuilder();

        private Block(String name) {
            this.name = name;
        }

        private void append(String line) {
            if (line != null) {
                body.append(line).append('\n');
            }
        }
    }
}
