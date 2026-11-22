package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** 조각이 남긴 API 버전({@link Resource#apiVersion()})을 operation 별로 모으고 순서를 정한다. */
final class ApiVersions {
    /** 버전을 나누는 문자 ({@code 1.1}, {@code 2-beta}). */
    private static final Pattern PARTS = Pattern.compile("[.\\-_]");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,9}");

    /** 낮은 버전이 먼저. 마디마다 둘 다 숫자면 숫자로, 아니면 글자로 비교한다 ({@code 1.9} &lt; {@code 1.10}). */
    static final Comparator<String> ORDER = ApiVersions::compare;

    private ApiVersions() {
    }

    /** {@code <소문자 메서드> <경로>} → 그 operation 을 문서화한 버전 (낮은 버전부터). 버전 없는 operation 은 없다. */
    static Map<String, SortedSet<String>> byOperation(List<Resource> resources) {
        Map<String, SortedSet<String>> versions = new LinkedHashMap<>();

        for (Resource resource : resources) {
            String version = resource.apiVersion();

            if (version != null && resource.method() != null) {
                versions.computeIfAbsent(key(resource.method(), resource.path()), k -> new TreeSet<>(ORDER))
                    .add(version);
            }
        }

        return versions;
    }

    static String key(String method, String path) {
        return method + " " + path;
    }

    private static int compare(String left, String right) {
        String[] a = PARTS.split(left);
        String[] b = PARTS.split(right);

        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int result = NUMBER.matcher(a[i]).matches() && NUMBER.matcher(b[i]).matches()
                ? Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]))
                : a[i].compareTo(b[i]);

            if (result != 0) {
                return result;
            }
        }

        return a.length != b.length ? Integer.compare(a.length, b.length) : left.compareTo(right);
    }
}
