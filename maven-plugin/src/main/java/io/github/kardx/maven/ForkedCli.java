package io.github.kardx.maven;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 라이브러리 본체(core)의 명령줄({@code DocumentationIndexCli}, {@code AsyncApiCli})을 프로젝트의 테스트 클래스패스로 따로 띄운다.
 * Gradle 플러그인의 {@code JavaExec} 와 같다. 같은 JVM 에서 부르지 않는 이유: 그 명령줄은 잘못된 인자에 프로세스를 끝내고,
 * 라이브러리 판은 플러그인이 아니라 프로젝트가 정한다.
 */
final class ForkedCli {
    private ForkedCli() {
    }

    static void run(List<String> classpath, String mainClass, String language, List<String> args, ToolLog log) {
        File java = new File(System.getProperty("java.home"), "bin/java");
        List<String> command = new ArrayList<>(List.of(java.getPath(), "-cp", String.join(File.pathSeparator,
            classpath)));

        if (language != null) {
            command.add("-Dkardx.language=" + language);
        }

        command.add(mainClass);
        command.addAll(args);

        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .start();

            try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output.lines().forEach(line -> log.lifecycle("{}", line));
            }

            int status = process.waitFor();

            if (status != 0) {
                throw new ToolingException(mainClass + " failed (exit code " + status + "). Check that "
                    + "io.github.kardx-project:kardx is a test dependency of this project.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ToolingException("Interrupted while running " + mainClass, e);
        }
    }
}
