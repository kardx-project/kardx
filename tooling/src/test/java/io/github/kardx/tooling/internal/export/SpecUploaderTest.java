package io.github.kardx.tooling.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 로컬 {@link HttpServer} 로 업로드({@link SpecUploader})를 확인한다. */
class SpecUploaderTest {
    private static final String TOKEN = "Bearer s3cret-token-value";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final List<String> received = new CopyOnWriteArrayList<>();
    private HttpServer server;

    @TempDir
    File dir;

    /** 응답을 미루는 핸들러가 있어도 다음 요청을 받도록 요청마다 스레드를 쓴다. */
    private ExecutorService executor;
    /** 응답을 미루는 핸들러를 테스트 끝에 풀어 준다. */
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException {
        executor = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stop() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void sendsTheFileWithHeadersAndContentTypeFromTheExtension() throws IOException {
        handle("/apis", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                + exchange.getRequestHeaders().getFirst("Authorization") + " "
                + exchange.getRequestHeaders().getFirst("Content-Type") + " "
                + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "");
        });

        int status = uploader("PUT", Map.of("Authorization", TOKEN), 0)
            .upload(file("openapi3.yaml", "openapi: 3.0.1"), uri("/apis/shop/openapi"));
        uploader("post", Map.of("content-type", "application/vnd.oai.openapi+json"), 0)
            .upload(file("openapi3.json", "{}"), uri("/apis/shop/openapi"));

        assertEquals(201, status);
        assertEquals(List.of(
            "PUT /apis/shop/openapi " + TOKEN + " application/yaml openapi: 3.0.1",
            "POST /apis/shop/openapi null application/vnd.oai.openapi+json {}"), received);
    }

    @Test
    void failsWithStatusAndTruncatedBodyWithoutLeakingHeaderValues() {
        String body = "denied for " + TOKEN + " " + "x".repeat(3000);

        handle("/apis", exchange -> respond(exchange, 403, body));

        ToolingException error = assertThrows(ToolingException.class,
            () -> uploader("PUT", Map.of("Authorization", TOKEN), 0).upload(file("openapi3.yaml", "a: 1"),
                uri("/apis/x")));

        String expected = "Upload of openapi3.yaml to PUT " + uri("/apis/x") + " failed with HTTP 403";

        assertTrue(error.getMessage().contains(expected), error.getMessage());
        assertTrue(error.getMessage().contains("Response body (first 2000 of " + body.length() + " characters)"),
            error.getMessage());
        assertTrue(error.getMessage().contains("denied for ****"), error.getMessage());
        assertFalse(error.getMessage().contains("s3cret-token-value"), error.getMessage());
    }

    @Test
    void retriesServerErrorsOnlyWhenAsked() throws IOException {
        AtomicInteger calls = new AtomicInteger();

        handle("/apis", exchange -> respond(exchange, calls.incrementAndGet() == 1 ? 503 : 200, "busy"));

        assertEquals(200, uploader("PUT", Map.of(), 2).upload(file("a.yaml", "a: 1"), uri("/apis/x")));
        assertEquals(2, calls.get());

        calls.set(0);

        assertThrows(ToolingException.class, () -> uploader("PUT", Map.of(), 0).upload(file("a.yaml", "a: 1"),
            uri("/apis/x")));
        assertEquals(1, calls.get(), "기본값은 다시 보내지 않는다");
    }

    @Test
    void retriesTooManyRequestsButNotOtherClientErrors() throws IOException {
        AtomicInteger calls = new AtomicInteger();

        handle("/limited", exchange -> respond(exchange, calls.incrementAndGet() == 1 ? 429 : 200, ""));
        handle("/missing", exchange -> {
            received.add("missing");
            respond(exchange, 404, "no such api");
        });

        assertEquals(200, uploader("PUT", Map.of(), 3).upload(file("a.yaml", "a: 1"), uri("/limited")));
        assertEquals(2, calls.get());

        ToolingException error = assertThrows(ToolingException.class,
            () -> uploader("PUT", Map.of(), 3).upload(file("a.yaml", "a: 1"), uri("/missing")));

        assertTrue(error.getMessage().contains("failed with HTTP 404"), error.getMessage());
        assertEquals(List.of("missing"), received, "4xx(429 제외)는 다시 보내도 같으므로 한 번만 보낸다");
    }

    @Test
    void timesOutAndRetriesWhenTheServerDoesNotAnswer() throws IOException {
        AtomicInteger calls = new AtomicInteger();

        handle("/slow", exchange -> {
            if (calls.incrementAndGet() == 1) {
                await();
            }

            respond(exchange, 200, "");
        });
        handle("/hang", exchange -> {
            received.add("hang");
            await();
        });

        Duration timeout = Duration.ofMillis(300);

        assertEquals(200, uploader("PUT", Map.of(), 1, timeout).upload(file("a.yaml", "a: 1"), uri("/slow")));
        assertEquals(2, calls.get());

        ToolingException error = assertThrows(ToolingException.class,
            () -> uploader("PUT", Map.of(), 0, timeout).upload(file("a.yaml", "a: 1"), uri("/hang")));

        assertTrue(error.getMessage().contains("failed: HttpTimeoutException"), error.getMessage());
        assertEquals(List.of("hang"), received);
    }

    @Test
    void masksUserInfoAndQueryValuesOfTheUrl() {
        handle("/apis", exchange -> {
            received.add(exchange.getRequestURI().getRawQuery());
            respond(exchange, 500, "echo " + exchange.getRequestURI());
        });

        URI uri = URI.create("http://deploy:p4ss-word-value@" + server.getAddress().getHostString() + ":"
            + server.getAddress().getPort() + "/apis/x?token=query-s3cret%2Bvalue&v=2");
        ToolingException error = assertThrows(ToolingException.class,
            () -> uploader("PUT", Map.of(), 1).upload(file("a.yaml", "a: 1"), uri));

        assertEquals(List.of("token=query-s3cret%2Bvalue&v=2", "token=query-s3cret%2Bvalue&v=2"), received);
        assertTrue(error.getMessage().contains("to PUT http://****@"), error.getMessage());
        assertTrue(error.getMessage().contains("/apis/x?token=****&v=2 failed with HTTP 500"), error.getMessage());
        assertTrue(error.getMessage().contains("echo /apis/x?token=****&v=2"), error.getMessage());

        for (String secret : List.of("p4ss-word-value", "query-s3cret")) {
            assertFalse(error.getMessage().contains(secret), error.getMessage());
        }
    }

    @Test
    void doesNotFollowRedirectsSoHeadersStayWithTheConfiguredServer() {
        handle("/apis", exchange -> {
            exchange.getResponseHeaders().add("Location", uri("/elsewhere").toString());
            respond(exchange, 307, "");
        });
        handle("/elsewhere", exchange -> {
            received.add("followed");
            respond(exchange, 200, "");
        });

        ToolingException error = assertThrows(ToolingException.class,
            () -> uploader("PUT", Map.of("Authorization", TOKEN), 0).upload(file("a.yaml", "a: 1"), uri("/apis")));

        assertTrue(error.getMessage().contains("failed with HTTP 307"), error.getMessage());
        assertTrue(received.isEmpty(), received.toString());
    }

    @Test
    void validatesMethodAndUrl() {
        assertEquals("POST", SpecUploader.method(" post ", "m"));
        assertThrows(ToolingException.class, () -> SpecUploader.method("GET", "m"));
        assertThrows(ToolingException.class, () -> SpecUploader.uri("ftp://example.com/x", "u"));
        assertThrows(ToolingException.class, () -> SpecUploader.uri("/relative", "u"));
        assertEquals("application/json", SpecUploader.contentType(new File("a.json")));
        assertEquals("application/yaml", SpecUploader.contentType(new File("a.yml")));
    }

    private SpecUploader uploader(String method, Map<String, String> headers, int retries) {
        return uploader(method, headers, retries, TIMEOUT);
    }

    private SpecUploader uploader(String method, Map<String, String> headers, int retries, Duration timeout) {
        return new SpecUploader(new SpecUploader.Settings(SpecUploader.method(method, "m"), headers, timeout, retries,
            Duration.ofMillis(10)), ToolLog.NONE);
    }

    /** 테스트가 끝날 때까지 응답하지 않는다. */
    private void await() {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(String path, Handler handler) {
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
    }

    private URI uri(String path) {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path);
    }

    private File file(String name, String text) throws IOException {
        File file = new File(dir, name);

        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);

        return file;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** 예외를 던지는 핸들러. */
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
