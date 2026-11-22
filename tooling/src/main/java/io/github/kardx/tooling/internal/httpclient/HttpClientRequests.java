package io.github.kardx.tooling.internal.httpclient;

import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.openapi.PrimaryResources;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * 문서화한 operation 마다 대표 요청 하나를 고른다. 같은 operation({@code resource.json} 의 메서드·경로 템플릿)을 여러 테스트가
 * 문서화했으면 OpenAPI 와 같은 순서({@link PrimaryResources#statusPriority})로 성공 응답 테스트를 고르고, 같은 순위면 디렉터리
 * 이름순으로 앞선 것을 쓴다. 이름(operationId)과 제목(summary)은 후처리한 OpenAPI 스펙의 값을, 없으면 라이브러리가 남긴
 * 후보와 조각의 summary 를 쓴다.
 */
public final class HttpClientRequests {
    public static final String RESOURCE_FILE = "resource.json";
    public static final String EXTRAS_FILE = "openapi-extras.json";
    public static final String REQUEST_SNIPPET = "http-request.adoc";
    private static final String SUMMARY = "summary";
    private static final Pattern NOT_NAME = Pattern.compile("[^A-Za-z0-9_-]+");
    /** 대표 조각이 먼저 오는 순서: 응답 상태 순위, 디렉터리 경로. */
    private static final Comparator<Fragment> FIRST = Comparator
        .comparingInt((Fragment f) -> PrimaryResources.statusPriority(f.status()))
        .thenComparing(f -> f.directory().getPath());

    private HttpClientRequests() {
    }

    /**
     * 파일에 쓸 요청 하나.
     *
     * @param name {@code # @name} 값 (operationId)
     * @param title {@code ###} 뒤의 제목 (OpenAPI operation 의 summary, 없으면 name)
     * @param request 보낼 요청
     */
    public record Entry(String name, String title, RecordedRequest request) {
    }

    /** 조각 하나: 디렉터리, {@code resource.json}, 응답 상태. */
    private record Fragment(File directory, Map<String, Object> resource, int status) {
        Map<String, Object> request() {
            return SpecMaps.mapOrEmpty(resource.get("request"));
        }

        String method() {
            return String.valueOf(request().get("method")).toLowerCase(Locale.ROOT);
        }

        String path() {
            return String.valueOf(request().get("path"));
        }
    }

    /**
     * @param resourceFiles {@code resource.json} 들 (다른 파일은 건너뛴다)
     * @param specs 후처리한 OpenAPI 스펙 후보. 있는 첫 파일에서 operationId 를 읽는다
     * @return 경로·메서드 순의 요청
     */
    public static List<Entry> collect(Iterable<File> resourceFiles, Iterable<File> specs) {
        // 경로, 메서드 순
        Map<String, Fragment> primary = new TreeMap<>();

        for (File file : resourceFiles) {
            if (file.getName().equals(RESOURCE_FILE)) {
                Map<String, Object> resource = SpecMaps.mapOrEmpty(JsonFiles.read(file));
                Map<String, Object> response = SpecMaps.mapOrEmpty(resource.get("response"));
                int status = response.get("status") instanceof Number n ? n.intValue() : 0;
                Fragment fragment = new Fragment(file.getParentFile(), resource, status);

                primary.merge(fragment.path() + " " + fragment.method(), fragment,
                    (a, b) -> FIRST.compare(a, b) <= 0 ? a : b);
            }
        }

        Map<String, Map<String, Object>> operations = operations(specs);
        List<Entry> entries = new ArrayList<>();

        primary.values().forEach(fragment -> {
            Map<String, Object> operation = operations.get(fragment.method() + " " + fragment.path());
            String name = name(fragment, SpecMaps.string(operation, "operationId"));
            String summary = summary(fragment, operation);

            entries.add(new Entry(name, summary == null || summary.isBlank() ? name : summary, request(fragment)));
        });

        return entries;
    }

    /** 후처리한 스펙의 summary({@code openApi { summary }} 를 반영한 값). 스펙에 없으면 조각의 summary. */
    private static String summary(Fragment fragment, Map<String, Object> operation) {
        String summary = SpecMaps.string(operation, SUMMARY);

        return summary == null || summary.isBlank() ? SpecMaps.string(fragment.resource(), SUMMARY) : summary;
    }

    private static RecordedRequest request(Fragment fragment) {
        File snippet = new File(fragment.directory(), REQUEST_SNIPPET);
        RecordedRequest parsed = snippet.isFile() ? RecordedRequest.parse(TextFiles.read(snippet)) : null;

        return parsed == null ? RecordedRequest.fromResource(fragment.request()) : parsed;
    }

    /** {@code # @name} 값. 요청 변수 이름으로 쓰이므로 글자·숫자·{@code _}·{@code -} 밖의 문자는 {@code -} 로 바꾼다. */
    private static String name(Fragment fragment, String operationId) {
        if (operationId != null) {
            return NOT_NAME.matcher(operationId).replaceAll("-");
        }

        File extras = new File(fragment.directory(), EXTRAS_FILE);
        Map<String, Object> candidate = extras.isFile()
            ? SpecMaps.map(SpecMaps.mapOrEmpty(JsonFiles.read(extras)), "operationId")
            : null;
        String id = SpecMaps.string(candidate, "id");

        if (id == null) {
            id = SpecMaps.string(fragment.resource(), "operationId");
        }

        return NOT_NAME.matcher(id == null ? fragment.directory().getName() : id).replaceAll("-");
    }

    /** {@code <소문자 메서드> <경로>} → 후처리한 스펙의 operation. 스펙이 없으면 빈 맵. */
    private static Map<String, Map<String, Object>> operations(Iterable<File> specs) {
        Map<String, Map<String, Object>> operations = new LinkedHashMap<>();

        for (File spec : specs) {
            Map<String, Object> document = spec.isFile() ? OpenApiSpecFiles.readOpenApi3(spec) : null;

            if (document == null) {
                continue;
            }

            SpecMaps.mapOrEmpty(document.get(SpecMaps.PATHS)).forEach((path, item) -> {
                for (String method : OpenApiSpecFiles.OPERATION_METHODS) {
                    Map<String, Object> operation = SpecMaps.map(SpecMaps.map(item), method);

                    if (operation != null) {
                        operations.put(method + " " + path, operation);
                    }
                }
            });

            return operations;
        }

        return operations;
    }
}
