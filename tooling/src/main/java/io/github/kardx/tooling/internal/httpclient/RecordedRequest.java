package io.github.kardx.tooling.internal.httpclient;

import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 문서화 테스트가 보낸 요청 하나. REST Docs {@code http-request.adoc} 스니펫(실제 요청)에서 읽고, 없으면
 * {@code resource.json}(경로 템플릿, 헤더 예시, 본문 예시)에서 만든다.
 *
 * @param method HTTP 메서드
 * @param target 경로와 쿼리 ({@code /api/v1/users?page=0})
 * @param headers {@code 이름: 값} 줄 ({@code Host}·{@code Content-Length} 제외)
 * @param body 본문. 없으면 빈 글
 */
public record RecordedRequest(String method, String target, List<String> headers, String body) {
    private static final String DELIMITER = "----";
    /** 클라이언트가 정하는 헤더. 파일에 적지 않는다. */
    private static final Set<String> SKIPPED_HEADERS = Set.of("host", "content-length");
    private static final Pattern HTTP_VERSION = Pattern.compile("\\s+HTTP/\\d(\\.\\d)?$");
    private static final Pattern ABSOLUTE = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*");

    /** {@code http-request.adoc} 의 첫 소스 블록을 읽는다. 요청 줄이 없으면 null. */
    public static RecordedRequest parse(String snippet) {
        List<String> lines = block(snippet);

        if (lines.isEmpty() || lines.get(0).isBlank()) {
            return null;
        }

        String requestLine = HTTP_VERSION.matcher(lines.get(0).trim()).replaceFirst("");
        int space = requestLine.indexOf(' ');

        if (space < 0) {
            return null;
        }

        List<String> headers = new ArrayList<>();
        int index = 1;

        for (; index < lines.size() && !lines.get(index).isBlank(); index++) {
            String header = lines.get(index);
            int colon = header.indexOf(':');

            if (colon > 0 && !SKIPPED_HEADERS.contains(header.substring(0, colon).trim().toLowerCase(Locale.ROOT))) {
                headers.add(header);
            }
        }

        String body = index < lines.size() ? String.join("\n", lines.subList(index + 1, lines.size())).strip() : "";
        String target = ABSOLUTE.matcher(requestLine.substring(space + 1).trim()).replaceFirst("");

        return new RecordedRequest(requestLine.substring(0, space), target.isEmpty() ? "/" : target, headers, body);
    }

    /** {@code resource.json} 의 요청에서 만든다 (경로 템플릿, 예시가 있는 헤더, 본문 예시). */
    static RecordedRequest fromResource(Map<String, Object> request) {
        List<String> headers = new ArrayList<>();
        String contentType = SpecMaps.string(request, "contentType");

        if (contentType != null) {
            headers.add("Content-Type: " + contentType);
        }

        for (Map<String, Object> header : SpecMaps.maps(request.get("headers"))) {
            Object example = header.get("example");

            if (example != null) {
                headers.add(header.get("name") + ": " + example);
            }
        }

        String body = SpecMaps.string(request, "example");

        return new RecordedRequest(String.valueOf(request.get("method")), String.valueOf(request.get("path")),
            headers, body == null ? "" : body);
    }

    /** 첫 {@code ----} 블록의 줄들. 블록이 없으면 글 전체. */
    private static List<String> block(String snippet) {
        List<String> all = List.of(snippet.replace("\r\n", "\n").split("\n", -1));
        int start = all.indexOf(DELIMITER);

        if (start < 0) {
            return all;
        }

        int end = all.subList(start + 1, all.size()).indexOf(DELIMITER);

        return all.subList(start + 1, end < 0 ? all.size() : start + 1 + end);
    }
}
