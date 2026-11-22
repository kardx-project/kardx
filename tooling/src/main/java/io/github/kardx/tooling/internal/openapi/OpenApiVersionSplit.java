package io.github.kardx.tooling.internal.openapi;

import static io.github.kardx.tooling.internal.spec.SpecMaps.PATHS;

import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code apiVersioning { splitSpecs = true }} 이면 후처리를 마친 스펙({@code openapi3.yaml})에서 버전마다 스펙
 * ({@code openapi3-v<버전>.yaml})을 만든다. 각 스펙에는 그 버전을 문서화한 operation 과 버전 없는 operation 이 들어간다.
 * 원래 스펙은 그대로 둔다 (가장 높은 버전, {@link OpenApiApiVersions}). epages 가 같은 경로·메서드의 조각을 하나로 합치므로
 * 버전별 스펙의 본문 스키마는 모든 버전의 합이다. 지난 실행의 버전 스펙은 {@code openapi3} 가 돌기 전에
 * {@link io.github.kardx.tooling.internal.spec.PreviousSpecFiles} 가 지운다.
 */
public final class OpenApiVersionSplit {
    /** 버전 스펙 이름 ({@code <원래 이름>-v<버전>.<확장자>}). */
    private static final Pattern SPLIT_NAME = Pattern.compile("(.+)-v[^/]+\\.(yaml|yml|json)");
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");

    private OpenApiVersionSplit() {
    }

    /** @return 만든 버전 스펙 파일 */
    public static List<File> apply(File snippetsDirectory, File specDirectory, ApiVersioningSettings settings) {
        List<File> written = new ArrayList<>();

        if (!settings.splitSpecs()) {
            return written;
        }

        Map<String, SortedSet<String>> versions = ApiVersions.byOperation(OpenApiResources.read(snippetsDirectory));
        SortedSet<String> all = new TreeSet<>(ApiVersions.ORDER);

        versions.values().forEach(all::addAll);

        if (all.isEmpty()) {
            return written;
        }

        for (File spec : OpenApiSpecFiles.list(specDirectory)) {
            Map<String, Object> document = isSplit(spec) ? null : OpenApiSpecFiles.readOpenApi3(spec);

            if (document != null) {
                all.forEach(version -> written.add(write(spec, version(document, versions, version, settings),
                    version)));
            }
        }

        return written;
    }

    private static Map<String, Object> version(Map<String, Object> document, Map<String, SortedSet<String>> versions,
        String version, ApiVersioningSettings settings) {
        Map<String, Object> copy = SpecMaps.mapOrEmpty(SpecMaps.deepCopy(document));
        Iterator<Map.Entry<String, Object>> paths = SpecMaps.mapOrEmpty(copy.get(PATHS)).entrySet().iterator();

        while (paths.hasNext()) {
            Map.Entry<String, Object> path = paths.next();
            Map<String, Object> item = SpecMaps.mapOrEmpty(path.getValue());

            for (String method : OpenApiSpecFiles.OPERATION_METHODS) {
                SortedSet<String> documented = versions.get(ApiVersions.key(method, path.getKey()));
                Map<String, Object> operation = SpecMaps.map(item, method);

                if (operation == null || documented == null) {
                    continue;
                }

                if (documented.contains(version)) {
                    OpenApiApiVersions.mark(operation, version, settings);
                } else {
                    item.remove(method);
                }
            }

            if (OpenApiSpecFiles.OPERATION_METHODS.stream().noneMatch(item::containsKey)) {
                paths.remove();
            }
        }

        return copy;
    }

    private static File write(File spec, Map<String, Object> document, String version) {
        String name = spec.getName();
        int dot = name.lastIndexOf('.');
        File target = new File(spec.getParentFile(), name.substring(0, dot) + "-v"
            + UNSAFE.matcher(version).replaceAll("_") + name.substring(dot));

        OpenApiSpecFiles.write(target, document);

        return target;
    }

    /** 같은 디렉터리에 원래 이름의 스펙이 있는 {@code <이름>-v<버전>.<확장자>} 인지 (버전 스펙). */
    public static boolean isSplit(File spec) {
        Matcher matcher = SPLIT_NAME.matcher(spec.getName());

        return matcher.matches() && new File(spec.getParentFile(), matcher.group(1) + "." + matcher.group(2)).isFile();
    }
}
