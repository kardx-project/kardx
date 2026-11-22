package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.internal.PluginConstants;
import java.util.List;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

/**
 * 테스트 JVM 에 설명 색인 위치를 {@value PluginConstants#SOURCE_DOCS_PROPERTY} 로 넘긴다. 파일 내용만 테스트 입력이 되므로
 * (경로 무시) 프로젝트를 옮겨도 테스트 캐시가 맞는다. 파일이 없으면(기능을 껐으면) 아무것도 넘기지 않는다.
 */
public abstract class SourceDocsArgument implements CommandLineArgumentProvider {

    /** 설명 색인. */
    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getFile();

    @Override
    public Iterable<String> asArguments() {
        return getFile().isPresent()
            ? List.of("-D" + PluginConstants.SOURCE_DOCS_PROPERTY + "=" + getFile().get().getAsFile().getAbsolutePath())
            : List.of();
    }
}
