package io.github.kardx.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code apiDocsSourceDocs} (실험 기능, 기본 꺼짐): 기본값이면 태스크도 시스템 속성도 없는지, 켜면 Kotlin DSL 빌드에서
 * configuration cache 를 켜고 프로젝트 의존성의 소스까지 읽은 색인이 테스트 JVM 의 시스템 속성으로 닿는지, 두 번째 실행이
 * UP-TO-DATE 인지, 메서드 문서는 {@code methodDocs} 를 켰을 때만 모으는지(Groovy DSL 포함), {@code @param} 은 꺼져 있어도
 * 모으는지, 항목별 스위치가 색인에서 그 항목만 빼고 바꾸면 태스크가 다시 도는지. 테스트 JVM 의 JUnit 은 이 테스트의
 * 클래스패스에서 가져온다 (네트워크 없이).
 */
class SourceDocsFunctionalTest {
    private static final String TASK = ":app:apiDocsSourceDocs";
    private static final String INDEX = "app/build/kardx/source-docs/source-docs.json";
    private static final String SEEN = "app/build/seen-property.txt";
    private static final String GET_USER = "\"com.example.app.UserController#getUser\"";
    private static final String ENABLED = "sourceDocs { enabled = true }";
    private static final String METHOD_DOCS_ON = "sourceDocs { enabled = true; methodDocs = true }";
    private static final String APP_BUILD = """
        plugins {
            java
            id("io.github.kardx-project")
        }
        kardx {
            addDependencies = false
            %s
        }
        dependencies {
            implementation(project(":domain"))
            %s
        }
        tasks.test { useJUnitPlatform() }
        """;

    @TempDir
    File root;

    @BeforeEach
    void writeProjects() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"shop\"\ninclude(\"app\", \"domain\")\n");
        write("domain/build.gradle.kts", "plugins { java }\n");
        write("domain/src/main/java/com/example/domain/Money.java", """
            package com.example.domain;

            /** 금액 */
            public record Money(/** 통화 */ String currency, long amount) {
            }
            """);
        write("app/src/main/java/com/example/app/UserController.java", """
            package com.example.app;

            public class UserController {
                /** 사용자를 조회한다. 없으면 404. */
                public String getUser(long id) { return ""; }
            }
            """);
        write("app/src/test/java/com/example/app/PropertyTest.java", """
            package com.example.app;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import org.junit.jupiter.api.Test;

            class PropertyTest {
                @Test
                void recordsProperty() throws Exception {
                    String value = String.valueOf(System.getProperty("kardx.source-docs"));
                    Files.writeString(Path.of("build", "seen-property.txt"), value);
                }
            }
            """);
    }

    @Test
    void indexReachesTestJvmAndSecondRunIsUpToDate() throws IOException {
        write("app/build.gradle.kts", APP_BUILD.formatted(ENABLED, junitDependencies()));

        BuildResult first = runner(":app:test", "--configuration-cache").build();
        BuildResult second = runner(":app:test", "--configuration-cache").build();
        String index = Files.readString(new File(root, INDEX).toPath());

        assertEquals(TaskOutcome.SUCCESS, outcome(first, TASK));
        assertEquals(TaskOutcome.SUCCESS, outcome(first, ":app:test"));
        assertEquals(new File(root, INDEX).getCanonicalPath(),
            new File(Files.readString(new File(root, SEEN).toPath())).getCanonicalPath());
        assertTrue(index.contains("\"com.example.domain.Money\"") && index.contains("\"currency\": \"통화\""),
            "프로젝트 의존성의 소스도 읽는다\n" + index);
        assertFalse(index.contains("\"methods\""), "메서드 문서는 기본으로 모으지 않는다\n" + index);
        assertTrue(second.getOutput().contains("Configuration cache entry reused"), second.getOutput());
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, TASK));
        assertEquals(TaskOutcome.UP_TO_DATE, outcome(second, ":app:test"));
    }

    @Test
    void docChangeInDependencyRerunsIndexAndTest() throws IOException {
        write("app/build.gradle.kts", APP_BUILD.formatted(ENABLED, junitDependencies()));
        runner(":app:test", "--configuration-cache").build();
        write("domain/src/main/java/com/example/domain/Money.java", """
            package com.example.domain;

            /** 돈 */
            public record Money(String currency, long amount) {
            }
            """);

        BuildResult changed = runner(":app:test", "--configuration-cache").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(changed, TASK));
        assertEquals(TaskOutcome.SUCCESS, outcome(changed, ":app:test"), "색인 내용이 테스트 입력이다");
        assertTrue(Files.readString(new File(root, INDEX).toPath()).contains("\"description\": \"돈\""));
    }

    @Test
    void projectDependenciesCanBeLeftOut() throws IOException {
        write("app/build.gradle.kts", APP_BUILD.formatted(
            "sourceDocs { enabled = true; includeProjectDependencies = false }", junitDependencies()));

        runner(TASK, "--configuration-cache").build();

        String index = Files.readString(new File(root, INDEX).toPath());

        assertFalse(index.contains("Money"), index);
        assertTrue(index.contains("\"version\": 1"), index);
    }

    @Test
    void methodDocsCanBeTurnedOnAndTogglingRerunsTheTask() throws IOException {
        write("app/build.gradle.kts", APP_BUILD.formatted(ENABLED, junitDependencies()));
        runner(TASK, "--configuration-cache").build();
        assertFalse(Files.readString(new File(root, INDEX).toPath()).contains(GET_USER));
        write("app/build.gradle.kts", APP_BUILD.formatted(METHOD_DOCS_ON, junitDependencies()));

        BuildResult on = runner(TASK, "--configuration-cache").build();
        String index = Files.readString(new File(root, INDEX).toPath());

        assertEquals(TaskOutcome.SUCCESS, outcome(on, TASK), "methodDocs 는 태스크 입력이다");
        assertTrue(index.contains(GET_USER) && index.contains("\"summary\": \"사용자를 조회한다\""), index);

        write("app/build.gradle.kts", APP_BUILD.formatted(ENABLED, junitDependencies()));

        BuildResult off = runner(TASK, "--configuration-cache").build();

        assertEquals(TaskOutcome.SUCCESS, outcome(off, TASK));
        assertFalse(Files.readString(new File(root, INDEX).toPath()).contains(GET_USER));
    }

    @Test
    void handlerParamTagsAreIndexedWithoutMethodDocs() throws IOException {
        write("app/build.gradle.kts", APP_BUILD.formatted(ENABLED, junitDependencies()));
        write("app/src/main/java/com/example/app/MemoController.java", """
            package com.example.app;

            public class MemoController {
                /**
                 * 구현 메모: 캐시를 먼저 본다.
                 * @param id 메모 ID
                 */
                public String getMemo(long id) { return ""; }
            }
            """);

        runner(TASK, "--configuration-cache").build();

        String index = Files.readString(new File(root, INDEX).toPath());

        assertTrue(index.contains("\"com.example.app.MemoController#getMemo\"") && index.contains("\"id\": \"메모 ID\""),
            index);
        assertFalse(index.contains("구현 메모") || index.contains(GET_USER), "본문은 methodDocs 를 켰을 때만 적는다\n" + index);
    }

    @Test
    void methodDocsWorksInGroovyDsl() throws IOException {
        write("app/build.gradle", """
            plugins {
                id 'java'
                id 'io.github.kardx-project'
            }
            kardx {
                addDependencies = false
                sourceDocs {
                    enabled = true
                    methodDocs = true
                    fieldDocs = false
                }
            }
            dependencies {
                implementation project(':domain')
            }
            """);

        runner(TASK, "--configuration-cache").build();

        String index = Files.readString(new File(root, INDEX).toPath());

        assertTrue(index.contains(GET_USER), index);
        assertFalse(index.contains("\"currency\""), "fieldDocs = false 면 속성 설명을 적지 않는다\n" + index);
    }

    @Test
    void offByDefaultPassesNothingAndDoesNotRunTheTask() throws IOException {
        write("app/build.gradle.kts", APP_BUILD.formatted("", junitDependencies()));

        BuildResult result = runner(":app:test", "--configuration-cache").build();
        BuildResult direct = runner(TASK, "--configuration-cache").build();

        assertNull(result.task(TASK), "기본값(꺼짐)이면 test 가 태스크에 기대지 않는다");
        assertEquals("null", Files.readString(new File(root, SEEN).toPath()), "시스템 속성도 넘기지 않는다");
        assertEquals(TaskOutcome.SKIPPED, outcome(direct, TASK), "직접 불러도 건너뛴다");
        assertFalse(new File(root, INDEX).exists());
    }

    @Test
    void itemSwitchesLeaveOutTheirItemsAndTogglingRerunsTheTask() throws IOException {
        write("domain/src/main/java/com/example/domain/Status.java", """
            package com.example.domain;

            public enum Status {
                /** 활성 */
                ACTIVE
            }
            """);
        write("app/src/main/java/com/example/app/MemoController.java", """
            package com.example.app;

            public class MemoController {
                /** @param id 메모 ID */
                public String getMemo(long id) { return ""; }
            }
            """);
        write("app/build.gradle.kts", APP_BUILD.formatted(ENABLED, junitDependencies()));
        runner(TASK, "--configuration-cache").build();

        String all = Files.readString(new File(root, INDEX).toPath());

        assertTrue(all.contains("\"currency\": \"통화\"") && all.contains("\"ACTIVE\": \"활성\"")
            && all.contains("\"description\": \"금액\"") && all.contains("\"id\": \"메모 ID\""), all);

        for (String item : List.of("fieldDocs", "enumDocs", "schemaDescriptions", "paramDocs")) {
            write("app/build.gradle.kts", APP_BUILD.formatted(
                "sourceDocs { enabled = true; " + item + " = false }", junitDependencies()));

            BuildResult off = runner(TASK, "--configuration-cache").build();
            String index = Files.readString(new File(root, INDEX).toPath());

            assertEquals(TaskOutcome.SUCCESS, outcome(off, TASK), item + " 는 태스크 입력이다");
            assertEquals(item.equals("fieldDocs"), !index.contains("\"currency\""), item + "\n" + index);
            assertEquals(item.equals("enumDocs"), !index.contains("\"ACTIVE\""), item + "\n" + index);
            assertEquals(item.equals("schemaDescriptions"), !index.contains("\"금액\""), item + "\n" + index);
            assertEquals(item.equals("paramDocs"), !index.contains("\"메모 ID\""), item + "\n" + index);
        }
    }

    /** 이 테스트 JVM 의 JUnit Platform jar 들 (픽스처가 저장소 없이 테스트를 돌리게). */
    private static String junitDependencies() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(path -> path.contains("junit") || path.contains("opentest4j") || path.contains("apiguardian"))
            .map(path -> "testImplementation(files(\"" + path.replace("\\", "\\\\") + "\"))")
            .collect(Collectors.joining("\n    "));
    }

    private static TaskOutcome outcome(BuildResult result, String task) {
        return result.task(task) == null ? null : result.task(task).getOutcome();
    }

    /**
     * 파일 시스템 감시를 끈다. 빌드가 바쁠 때 감시 이벤트가 늦으면 TestKit 데몬이 바뀌기 전 소스 스냅숏을 써서
     * 소스를 고친 실행이 UP_TO_DATE 가 될 수 있다 (루트 build 에서 간헐 실패).
     */
    private GradleRunner runner(String... arguments) {
        List<String> all = new ArrayList<>(List.of(arguments));

        all.add("--no-watch-fs");

        return GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(all);
    }

    private void write(String path, String text) throws IOException {
        File file = new File(root, path);

        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }
}
