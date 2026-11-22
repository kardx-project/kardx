package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gradle TestKit 으로 스펙 내보내기({@code apiDocsExport}), Maven 배포물 붙이기, 업로드({@code apiDocsUpload})를 확인한다. */
class SpecExportFunctionalTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /orders:
            get:
              responses:
                "200": {description: OK}
        """;
    /** 가짜 epages: openapi3 가 spec.yaml 을 결과 위치에 복사한다. */
    private static final String FAKE_OPENAPI3 = """
        tasks.matching { it.name == 'openapi3' }.configureEach { t ->
            def source = file('spec.yaml')
            def target = layout.buildDirectory.file('api-spec/openapi3.yaml').get().asFile
            t.doLast { target.parentFile.mkdirs(); target.text = source.text }
        }
        """;
    private static final String TOKEN = "s3cret-token-value";
    private static final String QUERY_KEY = "query-key-value";

    @Test
    void exportCopiesSelectedSpecsAndExplainsWhatIsMissing(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            version = '1.2.0'
            kardx {
                asyncApi { enabled = false }
                addDependencies = false
                export {
                    if (project.hasProperty('exportDir')) {
                        directory = layout.projectDirectory.dir(exportDir)
                    }
                    openApiFileName = '{name}-{version}.{ext}'
                    asyncApi = providers.gradleProperty('asyncApi').map { it.toBoolean() }.orElse(false)
                }
            }
            """ + FAKE_OPENAPI3);
        write(root, "spec.yaml", SPEC);
        // asyncApi.enabled 를 끄기 전 실행이 남긴 스펙. 끈 뒤에는 내보내지 않는다
        write(root, "build/kardx/asyncapi/asyncapi.yaml", "asyncapi: 3.0.0\n");

        BuildResult noDirectory = gradle(root, "apiDocsExport").buildAndFail();
        BuildResult noAsyncApi = gradle(root, "apiDocsExport", "-PexportDir=out", "-PasyncApi=true").buildAndFail();
        BuildResult exported = gradle(root, "apiDocsExport", "-PexportDir=out", "--configuration-cache").build();
        BuildResult again = gradle(root, "apiDocsExport", "-PexportDir=out", "--configuration-cache").build();
        BuildResult build = gradle(root, "build", "-PexportDir=out").build();

        assertTrue(noDirectory.getOutput().contains("kardx.export.directory is not set"),
            noDirectory.getOutput());
        assertTrue(noAsyncApi.getOutput().contains("No AsyncAPI spec: kardx.asyncApi.enabled is false")
            && noAsyncApi.getOutput().contains("kardx.export.asyncApi = false"), noAsyncApi.getOutput());
        assertEquals(TaskOutcome.SUCCESS, outcome(exported, ":apiDocsExport"));
        assertEquals(Files.readString(new File(root, "build/api-spec/openapi3.yaml").toPath()),
            Files.readString(new File(root, "out/shop-1.2.0.yaml").toPath()));
        assertTrue(again.getOutput().contains("Reusing configuration cache"), again.getOutput());
        assertEquals(TaskOutcome.SUCCESS, outcome(again, ":apiDocsExport"), "출력을 추적하지 않으므로 매번 복사한다");
        assertNull(build.task(":apiDocsExport"), "build 에 붙지 않는다");
        assertNull(build.task(":openapi3"), "build 가 스펙을 만들지 않는다");

        File plain = new File(root, "plain");

        write(plain, "settings.gradle", "rootProject.name = 'plain'\n");
        write(plain, "build.gradle", """
            plugins { id 'java'; id 'io.github.kardx-project' }
            kardx { addDependencies = false; export { directory = layout.buildDirectory.dir('out') } }
            """);

        BuildResult withoutEpages = gradle(plain, "apiDocsExport").buildAndFail();

        assertTrue(withoutEpages.getOutput().contains("the com.epages.restdocs-api-spec plugin is not applied"),
            withoutEpages.getOutput());
    }

    @Test
    void publishAttachesSpecsToMavenPublicationsOnlyWhenAsked(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins {
                id 'java'
                id 'maven-publish'
                id 'io.github.kardx-project'
                id 'com.epages.restdocs-api-spec'
            }
            group = 'com.example'
            version = '1.0'
            kardx {
                asyncApi { enabled = false }
                addDependencies = false
                export { publishSpecs = providers.gradleProperty('publishSpecs').map { it.toBoolean() }.orElse(true) }
            }
            publishing {
                publications { maven(MavenPublication) { from components.java } }
                repositories { maven { url = layout.buildDirectory.dir('repo') } }
            }
            """ + FAKE_OPENAPI3);
        write(root, "spec.yaml", SPEC);

        BuildResult off = gradle(root, "publish", "-PpublishSpecs=false").build();
        File module = new File(root, "build/repo/com/example/shop/1.0");

        assertNull(off.task(":openapi3"), "끄면 스펙을 만들지 않는다");
        assertTrue(new File(module, "shop-1.0.jar").isFile());
        assertFalse(new File(module, "shop-1.0-openapi.yaml").exists());

        BuildResult on = gradle(root, "publish", "--configuration-cache").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(on, ":apiDocsPublicationSpecs"));
        assertEquals(Files.readString(new File(root, "build/api-spec/openapi3.yaml").toPath()),
            Files.readString(new File(module, "shop-1.0-openapi.yaml").toPath()));
        assertTrue(new File(module, "shop-1.0.jar").isFile());

        // 고르지 않은 결과(문서 디렉터리)를 만드는 apiDocs 가 같은 실행에 있어도 의존성 없는 입력으로 실패하지 않는다
        BuildResult together = gradle(root, "apiDocs", "publish").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(together, ":apiDocsPublicationSpecs"));
    }

    @Test
    void uploadSendsSpecsAndNeverPrintsHeaderValues(@TempDir File root) throws IOException {
        List<String> received = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);

        server.createContext("/apis", exchange -> {
            try {
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization") + " "
                    + exchange.getRequestHeaders().getFirst("Content-Type"));
                // POST 는 거부하고, 받은 헤더를 본문에 그대로 돌려준다
                boolean reject = exchange.getRequestMethod().equals("POST");
                byte[] body = ("echo " + exchange.getRequestHeaders().getFirst("Authorization"))
                    .getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(reject ? 500 : 204, reject ? body.length : -1);

                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(reject ? body : new byte[0]);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            String url = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort()
                + "/apis/{name}/{version}/{type}.{ext}?key=" + QUERY_KEY;

            write(root, "settings.gradle", "rootProject.name = 'shop'\n");
            write(root, "build.gradle", """
                plugins {
                    id 'java'
                    id 'io.github.kardx-project'
                    id 'com.epages.restdocs-api-spec'
                }
                version = '2.0'
                kardx {
                    asyncApi { enabled = false }
                    addDependencies = false
                    upload {
                        url = providers.gradleProperty('url')
                        method = providers.gradleProperty('method').orElse('PUT')
                        headers.put('Authorization', providers.gradleProperty('token').map { 'Bearer ' + it })
                    }
                }
                """ + FAKE_OPENAPI3);
            write(root, "spec.yaml", SPEC);

            BuildResult noUrl = gradle(root, "apiDocsUpload", "-Ptoken=" + TOKEN).buildAndFail();
            BuildResult noToken = gradle(root, "apiDocsUpload", "-Purl=" + url).buildAndFail();
            BuildResult uploaded = gradle(root, "apiDocsUpload", "-Purl=" + url, "-Ptoken=" + TOKEN,
                "--configuration-cache").build();
            BuildResult rejected = gradle(root, "apiDocsUpload", "-Purl=" + url, "-Ptoken=" + TOKEN, "-Pmethod=POST")
                .buildAndFail();

            assertTrue(noUrl.getOutput().contains("kardx.upload.url is not set"), noUrl.getOutput());
            assertTrue(noToken.getOutput().contains("A header in kardx.upload.headers has no value"),
                noToken.getOutput());
            String path = "/apis/shop/2.0/openapi.yaml?key=" + QUERY_KEY;

            assertEquals(List.of("PUT " + path + " Bearer " + TOKEN + " application/yaml",
                "POST " + path + " Bearer " + TOKEN + " application/yaml"), received);
            assertTrue(uploaded.getOutput().contains("openapi.yaml?key=**** (headers:"), uploaded.getOutput());
            assertTrue(uploaded.getOutput().contains("Uploaded openapi3.yaml (HTTP 204)"), uploaded.getOutput());
            assertTrue(uploaded.getOutput().contains("headers: [Authorization, Content-Type]"), uploaded.getOutput());
            assertTrue(rejected.getOutput().contains("failed with HTTP 500") && rejected.getOutput().contains("echo ****"),
                rejected.getOutput());

            for (BuildResult result : List.of(uploaded, rejected)) {
                assertFalse(result.getOutput().contains(TOKEN), "헤더 값은 출력에 남지 않는다\n" + result.getOutput());
                assertFalse(result.getOutput().contains(QUERY_KEY), "주소의 쿼리 값도 남지 않는다\n" + result.getOutput());
            }
        } finally {
            server.stop(0);
        }
    }

    /** Stub Runner 는 {@code META-INF/<group>/<name>/<version>/mappings} 에서 찾는다. group 이 없으면 쓸모없는 jar 대신 실패한다. */
    @Test
    void stubsJarUsesTheGroupSetInTheBuildScriptAndNeedsOne(@TempDir File root) throws IOException {
        write(root, "settings.gradle", "rootProject.name = 'shop'\n");
        write(root, "build.gradle", """
            plugins { id 'java'; id 'io.github.kardx-project' }
            kardx {
                addDependencies = false
                // 테스트 없이 매핑만 둔다. build/ 아래면 Gradle 이 test 의 지난 출력으로 보고 지운다
                snippetsDirectory = layout.projectDirectory.dir('snippets')
                asyncApi { enabled = false }
                wireMock { enabled = true }
            }
            if (project.hasProperty('withGroup')) {
                group = 'com.shop'
            }
            version = '1.0'
            """);
        write(root, "snippets/wiremock/mappings/order.json", "{}");

        BuildResult noGroup = gradle(root, "apiDocsStubsJar").buildAndFail();

        assertTrue(noGroup.getOutput().contains("apiDocsStubsJar: project.group is not set"), noGroup.getOutput());

        gradle(root, "apiDocsStubsJar", "-PwithGroup", "--configuration-cache").build();

        try (ZipFile jar = new ZipFile(new File(root, "build/libs/shop-1.0-stubs.jar"))) {
            List<String> entries = jar.stream()
                .map(ZipEntry::getName)
                .toList();

            assertTrue(entries.contains("META-INF/com.shop/shop/1.0/mappings/order.json"), entries.toString());
        }
    }

    private static GradleRunner gradle(File root, String... arguments) {
        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(List.of(arguments));
    }

    private static TaskOutcome outcome(BuildResult result, String path) {
        return result.task(path) == null ? null : result.task(path).getOutcome();
    }

    private static void write(File root, String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
