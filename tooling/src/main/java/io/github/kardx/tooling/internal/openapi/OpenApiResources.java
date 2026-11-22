package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 스니펫 복사본의 OpenAPI 조각({@code resource.json})과 옆의 {@value #EXTRAS_FILE}(epages 가 담지 못하는 정보)을 읽는다.
 * 경로 순으로 정렬하므로 테스트 실행 순서와 상관없이 같은 목록이 나온다.
 */
public final class OpenApiResources {
    /** 라이브러리의 OpenApiOperationExtras.FILE_NAME 과 같다. */
    public static final String EXTRAS_FILE = "openapi-extras.json";
    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";
    /** 라이브러리가 선언한 응답을 남기는 {@value #EXTRAS_FILE} 의 키. */
    private static final String ERROR_RESPONSES = "errorResponses";

    private OpenApiResources() {
    }

    /**
     * 조각 하나.
     *
     * @param id 스니펫 디렉터리 기준 상대 경로 (정렬 기준)
     * @param json {@code resource.json}
     * @param extras {@value #EXTRAS_FILE}. 없으면 빈 맵
     */
    record Resource(String id, Map<String, Object> json, Map<String, Object> extras) {
        Map<String, Object> request() {
            return SpecMaps.map(json.get(REQUEST));
        }

        Map<String, Object> response() {
            return SpecMaps.map(json.get(RESPONSE));
        }

        String path() {
            return SpecMaps.string(request(), "path");
        }

        String method() {
            String method = SpecMaps.string(request(), "method");

            return method == null ? null : method.toLowerCase(Locale.ROOT);
        }

        int status() {
            return response().get("status") instanceof Number n ? n.intValue() : 0;
        }

        String operationId() {
            return SpecMaps.string(json, "operationId");
        }

        /**
         * 라이브러리가 남긴 operationId 후보 ({@code id}, 규칙 id 가 겹칠 때 쓸 {@code qualified}, 그대로 쓸지 {@code fixed}).
         * 규칙이 테스트 이름이거나 핸들러를 알 수 없었으면 null.
         */
        Map<String, Object> operationIdCandidate() {
            return SpecMaps.map(extras.get("operationId"));
        }

        String summary() {
            return SpecMaps.string(json, "summary");
        }

        /** 이 조각의 operation. 생성된 스펙에 없으면 null. */
        Map<String, Object> operation(Map<String, Object> document) {
            return SpecMaps.operation(document, path(), method());
        }

        /** 요청의 디스크립터 목록 ({@code pathParameters}, {@code requestFields} …). */
        List<Map<String, Object>> requestDescriptors(String key) {
            return SpecMaps.maps(request().get(key));
        }

        /** 응답의 디스크립터 목록 ({@code headers}, {@code responseFields}). */
        List<Map<String, Object>> responseDescriptors(String key) {
            return SpecMaps.maps(response().get(key));
        }

        /**
         * 테스트가 실제로 만들지 않고 선언만 한 응답 ({@code status}, {@code description}, {@code contentType}, 선택인
         * {@code schema}·{@code example}). 선언이 없으면 빈 목록.
         */
        List<Map<String, Object>> declaredResponses() {
            return SpecMaps.maps(extras.get(ERROR_RESPONSES));
        }

        /**
         * 다형 본문 정보 ({@code requestBodyPolymorphism}·{@code responseBodyPolymorphism}): {@code path}, {@code baseName},
         * {@code discriminator}, {@code variants}. 없으면 빈 목록.
         */
        List<Map<String, Object>> polymorphism(String key) {
            return SpecMaps.maps(extras.get(key));
        }

        /** 본문 필드의 Kotlin 기본값 ({@code fieldDefaults.requestBody}·{@code responseBody}: 필드 경로 → 값). 없으면 빈 맵. */
        Map<String, Object> fieldDefaults(String body) {
            return SpecMaps.mapOrEmpty(SpecMaps.map(SpecMaps.map(extras.get("fieldDefaults")), body));
        }

        /** 테스트가 선언한 OAuth2·OpenID Connect 요구사항 ({@code type}, {@code scopes}). 없으면 빈 목록. */
        List<Map<String, Object>> security() {
            return SpecMaps.maps(extras.get("security"));
        }

        /** 핸들러의 API 버전 ({@code apiVersion}). 없으면 null. */
        String apiVersion() {
            String version = SpecMaps.string(extras, "apiVersion");

            return version == null || version.isBlank() ? null : version;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Resource> read(File snippetsDirectory) {
        Path root = snippetsDirectory.toPath();
        List<Resource> resources = new ArrayList<>();

        for (Path file : files(snippetsDirectory)) {
            Path extras = file.resolveSibling(EXTRAS_FILE);
            Map<String, Object> extrasJson = Files.isRegularFile(extras)
                ? SpecMaps.map(JsonFiles.read(extras.toFile()))
                : null;

            resources.add(new Resource(
                root.relativize(file.getParent()).toString(),
                (Map<String, Object>) JsonFiles.read(file.toFile()),
                extrasJson == null ? Map.of() : extrasJson));
        }

        return resources;
    }

    /**
     * 스니펫 디렉터리 아래의 모든 {@code resource.json}. 스니펫 디렉터리 기준 경로 순이라 테스트 실행 순서나 파일 시스템의
     * 나열 순서와 상관없이 같은 목록이 나온다. 디렉터리가 없으면 빈 목록.
     */
    static List<Path> files(File snippetsDirectory) {
        if (!snippetsDirectory.isDirectory()) {
            return List.of();
        }

        Path root = snippetsDirectory.toPath();

        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.getFileName().toString().equals(OpenApiSchemaNames.RESOURCE_FILE))
                .sorted(Comparator.comparing(p -> root.relativize(p).toString()))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
