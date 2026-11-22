package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.tooling.internal.io.OutputDirectoryGuard;
import java.io.File;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

/** 이 프로젝트의 {@link OutputDirectoryGuard}. 빌드 디렉터리와 AsciiDoc 원본 위치는 늦게 바뀔 수 있어 실행할 때 읽는다. */
final class OutputGuards {
    private OutputGuards() {
    }

    static Provider<OutputDirectoryGuard> of(Project project, KardxExtension extension) {
        File projectDir = project.getProjectDir();
        File rootDir = project.getRootDir();

        return project.getLayout()
            .getBuildDirectory()
            .zip(extension.getAsciidocSourceDirectory(), (build, asciidoc) -> new OutputDirectoryGuard(
                projectDir, rootDir, build.getAsFile(), List.of(new File(projectDir, "src"), asciidoc.getAsFile())));
    }
}
