package io.github.kardx.maven;

import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.sourcedocs.SourceDocsOptions;
import io.github.kardx.tooling.internal.steps.SourceDocsStep;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * (실험 기능) main 소스의 KDoc·Javadoc 을 설명 색인으로 모은다 (Gradle {@code apiDocsSourceDocs}). 테스트가 읽으려면 surefire 의
 * {@code systemPropertyVariables} 에 {@code kardx.source-docs} 로 이 파일 경로를 넘긴다.
 */
@Mojo(name = "source-docs", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true)
public class SourceDocsMojo extends AbstractApiDocsMojo {
    /** 읽을 소스 디렉터리. 기본은 프로젝트의 main 소스 디렉터리(Kotlin 은 src/main/kotlin 도). */
    @Parameter
    List<File> sourceDirectories;

    /** 설명 색인 파일. */
    @Parameter(defaultValue = "${project.build.directory}/kardx/source-docs/source-docs.json")
    File sourceDocsFile;

    /** 메서드 KDoc 을 operation 설명으로 쓴다 (구현 메모가 공개 문서로 새지 않게 기본은 끔). */
    @Parameter(defaultValue = "false")
    boolean methodDocs;

    @Override
    void run(ToolLog log) {
        List<File> sources = new ArrayList<>();

        for (File directory : directories()) {
            sources.addAll(FileTrees.find(directory, List.of("**/*.kt", "**/*.java")));
        }

        SourceDocsStep.run(sources, new SourceDocsOptions(true, true, true, true, methodDocs), sourceDocsFile, log);
        log.lifecycle("Source docs: {}", sourceDocsFile);
    }

    private List<File> directories() {
        if (sourceDirectories != null && !sourceDirectories.isEmpty()) {
            return sourceDirectories;
        }

        List<File> directories = new ArrayList<>();

        project.getCompileSourceRoots().forEach(root -> directories.add(new File(root)));

        File kotlin = new File(project.getBasedir(), "src/main/kotlin");

        if (!directories.contains(kotlin)) {
            directories.add(kotlin);
        }

        return directories;
    }
}
