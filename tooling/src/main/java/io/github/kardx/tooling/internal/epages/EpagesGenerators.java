package io.github.kardx.tooling.internal.epages;

import com.epages.restdocs.apispec.model.ResourceModel;
import com.epages.restdocs.apispec.openapi3.OpenApi3Generator;
import com.epages.restdocs.apispec.postman.PostmanCollectionGenerator;
import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.swagger.v3.oas.models.servers.Server;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.kotlin.KotlinModule;

/**
 * epages restdocs-api-spec 의 생성기를 라이브러리로 불러 OpenAPI 3 기본 스펙과 Postman Collection 을 만든다. epages Gradle
 * 플러그인의 {@code openapi3}·{@code postman} 태스크와 같은 입력(스니펫 아래 {@code resource.json})과 같은 호출을 쓴다.
 * Gradle 플러그인은 epages 플러그인이 이 일을 하므로 이 클래스를 쓰지 않는다. 생성기는 {@code compileOnly} 라서 Maven 플러그인과
 * 명령줄 jar 가 런타임 의존으로 넣는다 ({@link #available()} 로 먼저 확인한다).
 */
public final class EpagesGenerators {
    /** epages {@code openapi3 { outputFileNamePrefix }} 기본값. */
    public static final String OPENAPI3_PREFIX = "openapi3";
    /** epages {@code postman { outputFileNamePrefix }} 기본값. */
    public static final String POSTMAN_PREFIX = "postman-collection";
    private static final String GENERATOR_CLASS = "com.epages.restdocs.apispec.openapi3.OpenApi3Generator";
    private static final String RESOURCE_FILE = "resource.json";

    private EpagesGenerators() {
    }

    /**
     * OpenAPI 기본 스펙 설정. epages {@code openapi3 { }} 와 같다.
     *
     * @param description {@code info.description}. 없으면 null
     * @param servers 서버 주소 ({@code servers[].url})
     * @param format {@code yaml} 또는 {@code json} (파일 확장자도 된다)
     * @param fileNamePrefix 파일 이름 앞부분 ({@value #OPENAPI3_PREFIX})
     */
    public record OpenApiSettings(String title, String version, String description, List<String> servers,
        String format, String fileNamePrefix) {
    }

    /** 생성기 jar(com.epages:restdocs-api-spec-openapi3-generator)가 클래스패스에 있는가. */
    public static boolean available() {
        try {
            Class.forName(GENERATOR_CLASS, false, EpagesGenerators.class.getClassLoader());

            return true;
        } catch (ClassNotFoundException e) {
            // 없으면 OpenAPI 생성을 할 수 없다는 뜻이다. 부르는 쪽이 의존을 더하라고 알린다
            return false;
        }
    }

    /**
     * {@code snippets} 아래 {@code resource.json} 으로 OpenAPI 3 스펙을 만들어 {@code directory} 에 쓴다.
     *
     * @return 쓴 파일 ({@code <prefix>.<format>})
     */
    public static File openApi3(File snippets, File directory, OpenApiSettings settings) {
        List<ResourceModel> resources = resources(snippets);
        List<Server> servers = new ArrayList<>();

        settings.servers().forEach(url -> servers.add(new Server().url(url)));

        String spec = OpenApi3Generator.INSTANCE.generateAndSerialize(resources, servers, settings.title(),
            settings.description(), Map.of(), settings.version(), null, settings.format(), null);
        File file = new File(directory, settings.fileNamePrefix() + "." + settings.format());

        TextFiles.write(file, spec);

        return file;
    }

    /**
     * Postman Collection(v2.1)을 {@code directory} 에 {@value #POSTMAN_PREFIX}{@code .json} 으로 쓴다.
     *
     * @param baseUrl 요청 주소 앞부분 ({@code {{baseUrl}}} 대신 들어갈 값)
     */
    public static File postman(File snippets, File directory, String title, String version, String baseUrl) {
        Object collection = PostmanCollectionGenerator.INSTANCE.generate(resources(snippets), title, version, baseUrl);
        JsonMapper mapper = JsonMapper.builder()
            .addModule(new KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();
        File file = new File(directory, POSTMAN_PREFIX + ".json");

        TextFiles.write(file, mapper.writeValueAsString(collection));

        return file;
    }

    private static List<ResourceModel> resources(File snippets) {
        JsonMapper mapper = JsonMapper.builder()
            .addModule(new KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
        List<ResourceModel> resources = new ArrayList<>();

        for (File file : resourceFiles(snippets)) {
            try {
                resources.add(mapper.readValue(file, ResourceModel.class));
            } catch (RuntimeException e) {
                throw new ToolingException("Could not read the OpenAPI fragment " + file + ": " + e.getMessage(), e);
            }
        }

        return resources;
    }

    /**
     * {@code resource.json} 파일을 epages Gradle 태스크와 같은 순서로 찾는다 (Kotlin {@code walkTopDown}: 디렉터리를 만나는 대로
     * 들어가는 깊이 우선, 디렉터리 안은 파일 시스템이 주는 순서). 스키마가 처음 나온 순서대로 쓰이므로 순서가 같아야 Gradle 과 같은
     * 스펙이 나온다.
     */
    private static List<File> resourceFiles(File snippets) {
        List<File> files = new ArrayList<>();

        if (snippets.isDirectory()) {
            walkTopDown(snippets, files);
        }

        return files;
    }

    private static void walkTopDown(File directory, List<File> found) {
        File[] children = directory.listFiles();

        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                walkTopDown(child, found);
            } else if (RESOURCE_FILE.equals(child.getName())) {
                found.add(child);
            }
        }
    }
}
