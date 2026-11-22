package io.github.kardx.tooling.internal.httpclient;

import io.github.kardx.tooling.internal.httpclient.HttpClientRequests.Entry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * IntelliJ HTTP Client 파일({@code .http})과 환경 파일({@value #ENV_FILE}, {@value #PRIVATE_ENV_FILE})을 만든다. 요청마다
 * {@code ### 제목}, {@code # @name operationId}, 요청 줄({@code {{baseUrl}}} + 경로), 헤더, 본문을 쓴다. Bearer 토큰 값(마스킹한
 * 값이나 가짜 JWT)은 {@code {{bearerToken}}} 변수로, 문서용으로 가린 다른 값({@code ****})은 이름을 딴 변수로 바꾸고
 * ({@link MaskedValues}) 두 환경 파일에 빈 값으로 선언한다. 실제 값은 넣지 않는다.
 */
public final class HttpClientFile {
    public static final String ENV_FILE = "http-client.env.json";
    /** IntelliJ 가 비밀 값을 두라고 권하는 환경 파일. 같은 이름의 변수는 이 파일 값이 앞선다. */
    public static final String PRIVATE_ENV_FILE = "http-client.private.env.json";
    static final String BASE_URL = "baseUrl";
    static final String TOKEN = "bearerToken";
    private static final String ENVIRONMENT = "local";
    private static final Pattern BEARER = Pattern.compile("(?i)^(Authorization:\\s*Bearer)\\s+.*$");

    private HttpClientFile() {
    }

    /** {@code .http} 파일 내용. */
    public static String render(List<Entry> entries) {
        List<String> blocks = new ArrayList<>();

        for (Entry entry : entries) {
            List<String> lines = new ArrayList<>();
            RecordedRequest request = MaskedValues.replace(bearer(entry.request())).request();

            lines.add("### " + entry.title().replace('\n', ' '));
            lines.add("# @name " + entry.name());
            lines.add(request.method() + " {{" + BASE_URL + "}}" + request.target());
            lines.addAll(request.headers());

            if (!request.body().isEmpty()) {
                lines.add("");
                lines.add(request.body());
            }

            blocks.add(String.join("\n", lines));
        }

        return String.join("\n\n", blocks) + "\n";
    }

    /** 환경 파일 내용: {@code local} 환경의 {@code baseUrl}, 그리고 비밀 변수({@link #secrets})를 빈 값으로. */
    public static Map<String, Object> environment(List<Entry> entries) {
        Map<String, Object> local = new LinkedHashMap<>();

        local.put(BASE_URL, "http://localhost:8080");
        secrets(entries).forEach(name -> local.put(name, ""));

        return Map.of(ENVIRONMENT, local);
    }

    /** 비밀 환경 파일 내용: {@code local} 환경의 비밀 변수를 빈 값으로. 비밀 변수가 없으면 빈 맵 (파일을 쓰지 않는다). */
    public static Map<String, Object> privateEnvironment(List<Entry> entries) {
        Map<String, Object> local = new LinkedHashMap<>();

        secrets(entries).forEach(name -> local.put(name, ""));

        return local.isEmpty() ? Map.of() : Map.of(ENVIRONMENT, local);
    }

    /** 요청이 쓰는 비밀 변수: Bearer 헤더가 있으면 {@code bearerToken}, 그다음 가린 값의 변수 (이름순). */
    private static List<String> secrets(List<Entry> entries) {
        SortedSet<String> masked = new TreeSet<>();
        boolean bearer = false;

        for (Entry entry : entries) {
            bearer |= entry.request().headers().stream()
                .anyMatch(header -> BEARER.matcher(header).matches());
            masked.addAll(MaskedValues.replace(bearer(entry.request())).variables());
        }

        List<String> secrets = new ArrayList<>();

        if (bearer) {
            secrets.add(TOKEN);
        }

        secrets.addAll(masked);

        return secrets;
    }

    private static RecordedRequest bearer(RecordedRequest request) {
        List<String> headers = request.headers().stream()
            .map(header -> BEARER.matcher(header).replaceFirst("$1 {{" + TOKEN + "}}"))
            .toList();

        return new RecordedRequest(request.method(), request.target(), headers, request.body());
    }
}
