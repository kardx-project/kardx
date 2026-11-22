package io.github.kardx.tooling.internal.sourcedocs;

import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Kotlin·Java 소스 파일에서 KDoc·Javadoc 을 모아 {@link SourceDocsIndex} 를 만든다. 컴파일러를 쓰지 않는 가벼운
 * 스캐너다 ({@link SourceLexer} → {@link KotlinDeclarations}·{@link JavaDeclarations}). 읽지 못하는 파일이나 예상하지 못한
 * 꼴은 건너뛰고(debug 로그) 나머지를 계속 읽는다. 빌드를 실패시키지 않는다. 소스는 UTF-8 로 읽는다. UTF-8 이 아닌 파일
 * ({@code options.encoding = "MS949"} 등)도 버리지 않고 깨진 글자만 U+FFFD 로 바꿔 읽으며, 그런 파일이 있으면 경고 한 줄을 남긴다.
 */
public final class SourceDocsScanner {
    private SourceDocsScanner() {
    }

    /**
     * {@code .kt}·{@code .java} 가 아닌 파일은 무시한다.
     *
     * @param options 색인에 적을 항목 ({@code sourceDocs} 의 항목별 스위치)
     * @param log UTF-8 이 아닌 파일 경고(warn)와 건너뛴 파일(debug)을 남길 곳
     */
    public static SourceDocsIndex scan(Iterable<File> files, SourceDocsOptions options, ToolLog log) {
        SourceDocsIndex index = new SourceDocsIndex(options);
        List<File> notUtf8 = new ArrayList<>();

        for (File file : files) {
            SourceLanguage language = SourceLanguage.of(file.getName());

            if (language != null) {
                scanFile(file, language, index, notUtf8, log);
            }
        }

        if (!notUtf8.isEmpty()) {
            log.warn("Source docs: {} source files are not UTF-8 (first: {}); characters that are not UTF-8 are "
                + "replaced with U+FFFD in their descriptions. Save the sources as UTF-8.", notUtf8.size(),
                notUtf8.get(0));
        }

        return index;
    }

    /** 소스 글 하나를 읽어 {@code index} 에 더한다. */
    static void scan(String source, SourceLanguage language, SourceDocsIndex index) {
        var tokens = SourceLexer.tokenize(source, language);

        if (language == SourceLanguage.KOTLIN) {
            KotlinDeclarations.parse(tokens, index);
        } else {
            JavaDeclarations.parse(tokens, index);
        }
    }

    private static void scanFile(
        File file, SourceLanguage language, SourceDocsIndex index, List<File> notUtf8, ToolLog log) {
        try {
            scan(read(file, notUtf8), language, index);
        } catch (IOException | RuntimeException e) {
            // 문서 설명은 부가 기능이다. 이 파일의 나머지 선언만 빠지고 빌드는 계속한다
            log.debug("Skipped source docs of {}: {}", file, e.toString());
        }
    }

    /** UTF-8 로 읽는다. UTF-8 이 아니면 [notUtf8] 에 더하고 잘못된 바이트를 U+FFFD 로 바꿔 읽는다. */
    private static String read(File file, List<File> notUtf8) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());

        try {
            return StandardCharsets.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            // 엄격한 디코더는 알리기 위해서만 쓴다. String 생성자는 잘못된 바이트를 U+FFFD 로 바꾼다
            notUtf8.add(file);

            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
