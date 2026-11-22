package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.internal.httpclient.HttpClientFile;
import io.github.kardx.tooling.internal.httpclient.HttpClientRequests;
import io.github.kardx.tooling.internal.io.FileTrees;
import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.File;
import java.util.List;
import java.util.Map;

/** 문서화한 요청을 IntelliJ HTTP Client 파일({@code .http})과 환경 파일로 쓴다. */
public final class HttpClientStep {
    /** 요청 스니펫 (스니펫 디렉터리 기준 Ant 패턴). */
    public static final List<String> SNIPPET_PATTERNS = List.of("**/" + HttpClientRequests.RESOURCE_FILE,
        "**/" + HttpClientRequests.EXTRAS_FILE, "**/" + HttpClientRequests.REQUEST_SNIPPET);
    /** 요청 스니펫에서 뺄 곳: 라이브러리가 남긴 엔드포인트 목록 등. */
    public static final List<String> SNIPPET_EXCLUDES = List.of(".kardx/**");

    private HttpClientStep() {
    }

    /**
     * {@code directory} 를 비우고 다시 쓴다.
     *
     * @param specs OpenAPI 스펙 후보 (있는 첫 파일에서 서버 주소를 읽는다)
     */
    public static void run(Iterable<File> snippetFiles, Iterable<File> specs, File directory, String fileName,
        ToolLog log) {
        List<HttpClientRequests.Entry> entries = HttpClientRequests.collect(snippetFiles, specs);

        FileTrees.delete(directory);
        TextFiles.write(new File(directory, fileName), HttpClientFile.render(entries));
        JsonFiles.writeReadable(new File(directory, HttpClientFile.ENV_FILE), HttpClientFile.environment(entries));

        Map<String, Object> secrets = HttpClientFile.privateEnvironment(entries);

        if (!secrets.isEmpty()) {
            JsonFiles.writeReadable(new File(directory, HttpClientFile.PRIVATE_ENV_FILE), secrets);
        }

        log.info("HTTP Client: wrote {} requests to {}", entries.size(), directory);
    }
}
