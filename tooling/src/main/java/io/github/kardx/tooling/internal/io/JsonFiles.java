package io.github.kardx.tooling.internal.io;

import groovy.json.JsonGenerator;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import java.io.File;
import java.util.List;
import java.util.Map;

/** UTF-8 JSON 파일 읽기·쓰기. Gradle 이 내장한 Groovy JSON 을 쓴다 (따로 의존성을 넣지 않는다). */
public final class JsonFiles {
    private static final String INDENT = "    ";
    private static final JsonGenerator READABLE = new JsonGenerator.Options()
        .disableUnicodeEscaping()
        .build();

    private JsonFiles() {
    }

    /** @return {@code Map}, {@code List} 또는 단일 값 */
    public static Object read(File file) {
        return new JsonSlurper().parse(file, "UTF-8");
    }

    /** JSON 글을 읽는다. 형식이 틀리면 {@link groovy.json.JsonException}. @return {@code Map}, {@code List} 또는 단일 값 */
    public static Object parse(String text) {
        return new JsonSlurper().parseText(text);
    }

    /** 들여쓴 JSON 으로 쓴다. */
    public static void write(File file, Object value) {
        TextFiles.write(file, toPrettyJson(value));
    }

    public static String toPrettyJson(Object value) {
        return JsonOutput.prettyPrint(JsonOutput.toJson(value));
    }

    /** 한 줄 JSON. */
    public static String toCompactJson(Object value) {
        return JsonOutput.toJson(value);
    }

    /**
     * 들여쓴 JSON 으로 쓰되 한글 등 ASCII 밖 글자를 그대로 둔다 ({@code \\uXXXX} 로 바꾸지 않는다). 사람이 여는 파일용.
     * {@link JsonOutput#prettyPrint(String)} 는 글자를 다시 이스케이프하므로 들여쓰기를 직접 한다.
     */
    public static void writeReadable(File file, Object value) {
        StringBuilder out = new StringBuilder();

        appendReadable(out, value, "");
        TextFiles.write(file, out.append('\n').toString());
    }

    private static void appendReadable(StringBuilder out, Object value, String indent) {
        String inner = indent + INDENT;

        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            List<String> entries = map.entrySet()
                .stream()
                .map(entry -> inner + READABLE.toJson(String.valueOf(entry.getKey())) + ": "
                    + readable(entry.getValue(), inner))
                .toList();

            out.append("{\n").append(String.join(",\n", entries)).append('\n').append(indent).append('}');
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            List<String> items = list.stream()
                .map(item -> inner + readable(item, inner))
                .toList();

            out.append("[\n").append(String.join(",\n", items)).append('\n').append(indent).append(']');
        } else {
            out.append(READABLE.toJson(value));
        }
    }

    private static String readable(Object value, String indent) {
        StringBuilder out = new StringBuilder();

        appendReadable(out, value, indent);

        return out.toString();
    }
}
