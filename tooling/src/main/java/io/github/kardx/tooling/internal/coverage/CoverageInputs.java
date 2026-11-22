package io.github.kardx.tooling.internal.coverage;

import io.github.kardx.tooling.internal.httpclient.RecordedRequest;
import io.github.kardx.tooling.internal.io.JsonFiles;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 범위 보고서의 입력을 읽는다: 엔드포인트 목록 파일들의 합과, 문서화한 operation({@code resource.json} 의 메서드·경로 템플릿,
 * 옆 {@code openapi-extras.json} 의 {@code apiVersion}).
 */
public final class CoverageInputs {
    /** 엔드포인트 목록이 있는 곳 (스니펫 디렉터리 기준). core 가 쓰는 위치와 같다. */
    public static final String ENDPOINTS_DIR = ".kardx/endpoints";
    public static final String RESOURCE_FILE = "resource.json";
    public static final String EXTRAS_FILE = "openapi-extras.json";
    /** REST Docs 요청 스니펫. OpenAPI 에서 뺀 operation({@code openApi { excluded = true }})에는 이것만 있다. */
    public static final String REQUEST_SNIPPET = "http-request.adoc";

    private CoverageInputs() {
    }

    /** 목록 파일들의 엔드포인트 합. 같은 메서드·경로·버전은 하나만 (파일 이름순으로 처음 본 것). */
    public static List<Endpoint> endpoints(Iterable<File> files) {
        Map<String, Endpoint> endpoints = new LinkedHashMap<>();

        for (File file : sorted(files)) {
            for (Map<String, Object> json : SpecMaps.maps(SpecMaps.mapOrEmpty(JsonFiles.read(file)).get("endpoints"))) {
                Endpoint endpoint = Endpoint.of(json);

                if (endpoint != null) {
                    endpoints.putIfAbsent(endpoint.method() + " " + PathPatterns.normalize(endpoint.path()) + " "
                        + endpoint.apiVersion(), endpoint);
                }
            }
        }

        return new ArrayList<>(endpoints.values());
    }

    /** 문서화한 operation. {@code resource.json} 이 아닌 파일은 건너뛴다. */
    public static List<Endpoint> documented(Iterable<File> files) {
        List<Endpoint> documented = new ArrayList<>();

        for (File file : sorted(files)) {
            if (!file.getName().equals(RESOURCE_FILE)) {
                continue;
            }

            Map<String, Object> request = SpecMaps.map(SpecMaps.mapOrEmpty(JsonFiles.read(file)), "request");
            File extras = new File(file.getParentFile(), EXTRAS_FILE);
            String version = extras.isFile()
                ? SpecMaps.string(SpecMaps.mapOrEmpty(JsonFiles.read(extras)), "apiVersion")
                : null;
            Map<String, Object> json = new LinkedHashMap<>(SpecMaps.mapOrEmpty(request));

            json.put("apiVersion", version);

            Endpoint operation = Endpoint.of(json);

            if (operation != null) {
                documented.add(operation);
            }
        }

        return documented;
    }

    /**
     * OpenAPI 에서 뺀 operation 의 실제 요청: {@code resource.json} 없이 {@value #REQUEST_SNIPPET} 만 있는 디렉터리의 메서드와
     * 실제 경로(쿼리 제외). 경로 템플릿을 모르므로 엔드포인트 패턴과 맞춰 본다 ({@link CoverageReport}).
     */
    public static List<Endpoint> excludedRequests(Iterable<File> files) {
        List<Endpoint> requests = new ArrayList<>();

        for (File file : sorted(files)) {
            if (!file.getName().equals(REQUEST_SNIPPET) || new File(file.getParentFile(), RESOURCE_FILE).isFile()) {
                continue;
            }

            RecordedRequest request = RecordedRequest.parse(TextFiles.read(file));

            if (request != null) {
                int query = request.target().indexOf('?');
                String path = query < 0 ? request.target() : request.target().substring(0, query);

                requests.add(new Endpoint(request.method().toUpperCase(Locale.ROOT), path, null, false, null));
            }
        }

        return requests;
    }

    private static List<File> sorted(Iterable<File> files) {
        List<File> list = new ArrayList<>();

        files.forEach(list::add);
        list.sort(Comparator.comparing(File::getPath));

        return list;
    }
}
