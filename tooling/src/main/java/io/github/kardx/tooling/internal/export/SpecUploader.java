package io.github.kardx.tooling.internal.export;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 스펙 파일 하나를 HTTP 로 보낸다 (JDK {@link HttpClient}). 2xx 가 아니면 상태 코드와 응답 본문 앞부분으로 실패한다.
 *
 * <ul>
 *   <li>헤더 값은 로그·오류 메시지에 남기지 않는다. 이름만 남기고, 응답 본문·예외 메시지에 값이 있으면 가린다({@link SecretMask})</li>
 *   <li>주소의 사용자 정보({@code user:password@})와 쿼리 파라미터 값도 로그·오류 메시지에서 가린다({@link UrlSecrets})</li>
 *   <li>리다이렉트는 따라가지 않는다: 인증 헤더가 다른 서버로 가지 않게 하고, 3xx 는 실패로 알린다</li>
 *   <li>{@code retries} 가 있으면 연결 실패·429·5xx 일 때 {@code backoff}, 그 두 배, … (최대 30초) 기다렸다가 다시 보낸다</li>
 * </ul>
 */
public final class SpecUploader {
    /** 오류 메시지에 넣는 응답 본문 길이. */
    static final int BODY_LIMIT = 2000;
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);
    private static final String CONTENT_TYPE = "Content-Type";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;
    private static final List<String> METHODS = List.of("PUT", "POST");

    private final Settings settings;
    private final ToolLog logger;
    private final HttpClient client;

    /**
     * 업로드 설정.
     *
     * @param method {@code PUT} 또는 {@code POST}
     * @param headers 요청 헤더 (값은 비밀일 수 있다)
     * @param timeout 연결·응답 대기 시간
     * @param retries 다시 보낼 횟수
     * @param backoff 첫 재시도 전 대기 시간
     */
    public record Settings(
        String method, Map<String, String> headers, Duration timeout, int retries, Duration backoff) {
    }

    public SpecUploader(Settings settings, ToolLog logger) {
        this.settings = settings;
        this.logger = logger;
        this.client = HttpClient.newBuilder()
            .connectTimeout(settings.timeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /**
     * {@code PUT}·{@code POST} 만 받는다 (대소문자 무관).
     *
     * @return 대문자 메서드
     */
    public static String method(String method, String property) {
        String upper = method.trim().toUpperCase(Locale.ROOT);

        if (!METHODS.contains(upper)) {
            throw new ToolingException(property + " must be PUT or POST, got '" + method + "'");
        }

        return upper;
    }

    /** 절대 http·https 주소. */
    public static URI uri(String url, String property) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);

            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                throw new ToolingException(property + " must be an absolute http or https URL, got '" + url + "'");
            }

            return uri;
        } catch (URISyntaxException e) {
            throw new ToolingException(property + " is not a valid URL: " + e.getMessage(), e);
        }
    }

    /**
     * [file] 을 [uri] 로 보낸다.
     *
     * @return 응답 상태 코드 (2xx)
     */
    public int upload(File file, URI uri) {
        SecretMask mask = mask(uri);
        HttpRequest request = request(file, uri, mask);

        logger.lifecycle("Uploading {} to {} {} (headers: {})", file.getName(), settings.method(),
            mask.apply(uri.toString()), request.headers().map().keySet());

        for (int attempt = 0; ; attempt++) {
            Attempt result = send(request, mask);

            if (result.succeeded()) {
                logger.lifecycle("Uploaded {} (HTTP {})", file.getName(), result.status());

                return result.status();
            }

            if (attempt >= settings.retries() || !result.retryable()) {
                throw new ToolingException(mask.apply(failure(file, uri, result)));
            }

            Duration wait = backoff(attempt);

            logger.warn("{} Retrying in {} ms ({} of {}).", mask.apply(failure(file, uri, result)), wait.toMillis(),
                attempt + 1, settings.retries());
            sleep(wait);
        }
    }

    /** 헤더 값과 [uri] 의 비밀값을 가리는 마스크. */
    private SecretMask mask(URI uri) {
        List<String> secrets = new ArrayList<>(settings.headers().values());

        secrets.addAll(UrlSecrets.of(uri));

        return new SecretMask(secrets);
    }

    private HttpRequest request(File file, URI uri, SecretMask mask) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(settings.timeout())
            .method(settings.method(), body(file));
        boolean contentType = false;

        for (Map.Entry<String, String> header : settings.headers().entrySet()) {
            contentType |= header.getKey().equalsIgnoreCase(CONTENT_TYPE);
            addHeader(builder, header.getKey(), header.getValue(), mask);
        }

        if (!contentType) {
            builder.header(CONTENT_TYPE, contentType(file));
        }

        return builder.build();
    }

    private static void addHeader(HttpRequest.Builder builder, String name, String value, SecretMask mask) {
        try {
            builder.header(name, value);
        } catch (IllegalArgumentException e) {
            // JDK 가 막는 이름(Host, Content-Length 등)이나 줄바꿈이 든 값. 값은 메시지에 넣지 않는다
            throw new ToolingException("Header '" + name + "' cannot be sent with the upload request: "
                + mask.apply(String.valueOf(e.getMessage())), e);
        }
    }

    private static HttpRequest.BodyPublisher body(File file) {
        try {
            return HttpRequest.BodyPublishers.ofFile(file.toPath());
        } catch (FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 파일 확장자로 정한 Content-Type. */
    static String contentType(File file) {
        return switch (GeneratedSpecs.extension(file).toLowerCase(Locale.ROOT)) {
            case "json" -> "application/json";
            case "yaml", "yml" -> "application/yaml";
            default -> "application/octet-stream";
        };
    }

    private Attempt send(HttpRequest request, SecretMask mask) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return new Attempt(response.statusCode(), response.body(), null);
        } catch (IOException e) {
            return new Attempt(0, "", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ToolingException("Upload of " + mask.apply(request.uri().toString()) + " was interrupted", e);
        }
    }

    private String failure(File file, URI uri, Attempt result) {
        String prefix = "Upload of " + file.getName() + " to " + settings.method() + " " + uri + " failed";

        if (result.error() != null) {
            return prefix + ": " + result.error().getClass().getSimpleName() + ": " + result.error().getMessage();
        }

        return prefix + " with HTTP " + result.status() + "." + responseBody(result.body());
    }

    private static String responseBody(String body) {
        if (body == null || body.isBlank()) {
            return " The response body is empty.";
        }

        String text = body.strip();

        if (text.length() <= BODY_LIMIT) {
            return " Response body:\n" + text;
        }

        return " Response body (first " + BODY_LIMIT + " of " + text.length() + " characters):\n"
            + text.substring(0, BODY_LIMIT);
    }

    private Duration backoff(int attempt) {
        Duration wait = settings.backoff().multipliedBy(1L << Math.min(attempt, 16));

        return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new ToolingException("Upload retry was interrupted", e);
        }
    }

    /** 한 번 보낸 결과. 연결하지 못했으면 {@code error} 가 있다. */
    private record Attempt(int status, String body, IOException error) {
        boolean succeeded() {
            return error == null && status / 100 == 2;
        }

        boolean retryable() {
            return error != null || status == TOO_MANY_REQUESTS || status >= SERVER_ERROR;
        }
    }
}
