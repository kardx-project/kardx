package io.github.kardx.tooling.internal.pipeline;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.io.JsonFiles;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ApiDocsConfig} 의 JSON 모양 맵을 만든다: 설정 파일을 읽고, 그 위에 명령줄 옵션이나 Maven 플러그인 파라미터를 덮는다.
 */
public final class ConfigMaps {
    private ConfigMaps() {
    }

    /** JSON 설정 파일을 바꿀 수 있는 맵으로 읽는다. */
    public static Map<String, Object> read(File file) {
        if (!file.isFile()) {
            throw new ToolingException("Config file not found: " + file);
        }

        if (!(JsonFiles.read(file) instanceof Map<?, ?> map)) {
            throw new ToolingException("Config file must contain a JSON object: " + file);
        }

        Map<String, Object> settings = new LinkedHashMap<>();

        map.forEach((key, value) -> settings.put(String.valueOf(key), copy(value)));

        return settings;
    }

    /** {@code path} 의 값을 {@code value} 로 둔다 (중간 객체가 없으면 만든다). {@code value} 가 null 이면 그대로 둔다. */
    @SuppressWarnings("unchecked")
    public static void put(Map<String, Object> settings, List<String> path, Object value) {
        if (value == null) {
            return;
        }

        Map<String, Object> current = settings;

        for (String key : path.subList(0, path.size() - 1)) {
            Object child = current.get(key);

            if (!(child instanceof Map)) {
                child = new LinkedHashMap<String, Object>();
                current.put(key, child);
            }

            current = (Map<String, Object>) child;
        }

        current.put(path.get(path.size() - 1), value);
    }

    private static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();

            map.forEach((key, item) -> copy.put(String.valueOf(key), copy(item)));

            return copy;
        }

        return value;
    }
}
