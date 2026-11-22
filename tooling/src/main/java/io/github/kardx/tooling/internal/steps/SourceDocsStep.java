package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.sourcedocs.SourceDocsIndex;
import io.github.kardx.tooling.internal.sourcedocs.SourceDocsOptions;
import io.github.kardx.tooling.internal.sourcedocs.SourceDocsScanner;
import java.io.File;
import java.util.Collection;

/** main 소스의 KDoc·Javadoc 을 설명 색인({@code source-docs.json})으로 모은다. 테스트 JVM 이 시스템 속성으로 읽는다. */
public final class SourceDocsStep {
    /** 설명 색인 파일 이름. */
    public static final String FILE_NAME = "source-docs.json";

    private SourceDocsStep() {
    }

    /** @param sources {@code .kt}·{@code .java} 파일 (다른 파일은 무시한다) */
    public static void run(Collection<File> sources, SourceDocsOptions options, File output, ToolLog log) {
        long started = System.nanoTime();
        SourceDocsIndex index = SourceDocsScanner.scan(sources, options, log);

        JsonFiles.writeReadable(output, index.toJson());
        log.info("Source docs: {} documented types from {} files in {} ms", index.typeCount(), sources.size(),
            (System.nanoTime() - started) / 1_000_000);
    }
}
