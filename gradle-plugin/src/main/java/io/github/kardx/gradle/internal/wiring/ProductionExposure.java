package io.github.kardx.gradle.internal.wiring;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.gradle.api.logging.Logger;

/**
 * 문서가 실행 jar 에 들어가는데({@code bootJar.enabled}) 민감 헤더를 가리지 않은 문서가 있으면 경고한다. 가리기 설정은 테스트
 * 런타임 설정(yml 의 {@code kardx.mask-sensitive-headers} 등)이라 빌드는 알 수 없으므로, 테스트가 문서마다 남긴 기록
 * ({@value IndexTasks#RECORDS_DIR}, {@code headers-masked})을 읽어 판정한다. 기록이 없으면(문서가 없음) 경고하지 않는다.
 */
final class ProductionExposure {
    private ProductionExposure() {
    }

    static void warnIfUnmasked(Logger logger, File asciidocDirectory) {
        List<String> unmasked = unmaskedDocuments(asciidocDirectory);

        if (unmasked.isEmpty()) {
            return;
        }

        logger.warn("kardx: API docs are packaged into the application (kardx.bootJar.enabled = "
            + "true), but {} documented test class(es) ran without sensitive header masking (e.g. {}). Real tokens or "
            + "cookies in documented requests would be served with the app. Enable "
            + "kardx.mask-sensitive-headers in the test configuration, or set "
            + "kardx { bootJar { enabled = false } } for production builds "
            + "(docs/build.md, section \"Production exposure\").",
            unmasked.size(), unmasked.get(0));
    }

    /** 가리기 없이 쓴 문서 이름. */
    static List<String> unmaskedDocuments(File asciidocDirectory) {
        File[] records = new File(asciidocDirectory, IndexTasks.RECORDS_DIR)
            .listFiles(file -> file.isFile() && file.getName().endsWith(".properties"));

        if (records == null) {
            return List.of();
        }

        return Arrays.stream(records)
            .sorted()
            .map(ProductionExposure::read)
            .filter(record -> !Boolean.parseBoolean(record.getProperty("headers-masked")))
            .map(record -> record.getProperty("document", "?"))
            .toList();
    }

    private static Properties read(File file) {
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.ISO_8859_1)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }

        return properties;
    }
}
