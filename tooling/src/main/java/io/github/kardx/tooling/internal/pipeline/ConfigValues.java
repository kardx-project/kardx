package io.github.kardx.tooling.internal.pipeline;

import io.github.kardx.tooling.ToolingException;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 설정 JSON 의 한 객체를 타입에 맞게 읽는다. 잘못된 값이나 모르는 키는 키 경로({@code openApi.viewer.type})를 담아
 * {@link ToolingException} 으로 알린다.
 */
final class ConfigValues {
    private final Map<?, ?> values;
    private final String path;
    private final File baseDir;

    private ConfigValues(Map<?, ?> values, String path, File baseDir) {
        this.values = values;
        this.path = path;
        this.baseDir = baseDir;
    }

    static ConfigValues root(Map<?, ?> values, File baseDir) {
        return new ConfigValues(values, "", baseDir);
    }

    /** 모르는 키가 있으면 실패한다 (오타를 조용히 넘기지 않는다). */
    ConfigValues allow(Set<String> keys) {
        for (Object key : values.keySet()) {
            if (!keys.contains(String.valueOf(key))) {
                throw new ToolingException("Unknown setting '" + name(String.valueOf(key)) + "'. Known settings"
                    + (path.isEmpty() ? "" : " in '" + path + "'") + ": " + String.join(", ", keys));
            }
        }

        return this;
    }

    ConfigValues child(String key) {
        Object value = values.get(key);

        if (value == null) {
            return new ConfigValues(Map.of(), name(key), baseDir);
        }

        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(key, "an object");
        }

        return new ConfigValues(map, name(key), baseDir);
    }

    String string(String key, String fallback) {
        Object value = values.get(key);

        if (value == null) {
            return fallback;
        }

        if (value instanceof Map || value instanceof List) {
            throw invalid(key, "a string");
        }

        return String.valueOf(value);
    }

    boolean bool(String key, boolean fallback) {
        Object value = values.get(key);

        if (value == null) {
            return fallback;
        }

        if (value instanceof Boolean flag) {
            return flag;
        }

        if ("true".equals(value) || "false".equals(value)) {
            return Boolean.parseBoolean((String) value);
        }

        throw invalid(key, "true or false");
    }

    /** 경로는 기준 디렉터리에 대한 상대 경로로도 쓸 수 있다. */
    File file(String key, File fallback) {
        String value = string(key, null);

        if (value == null) {
            return fallback;
        }

        File file = new File(value);

        return file.isAbsolute() ? file : new File(baseDir, value);
    }

    List<String> strings(String key) {
        Object value = values.get(key);

        if (value == null) {
            return List.of();
        }

        if (!(value instanceof List<?> list)) {
            throw invalid(key, "a list of strings");
        }

        List<String> strings = new ArrayList<>();

        list.forEach(item -> strings.add(String.valueOf(item)));

        return strings;
    }

    /** 값이 문자열인 객체. 순서를 지킨다. */
    Map<String, String> stringMap(String key) {
        return child(key).toStringMap();
    }

    /** 이 객체 전체를 문자열 맵으로. 순서를 지킨다. */
    Map<String, String> toStringMap() {
        Map<String, String> strings = new LinkedHashMap<>();

        values.forEach((name, value) -> strings.put(String.valueOf(name), String.valueOf(value)));

        return strings;
    }

    /** 키 이름 → 하위 객체. 순서를 지킨다. */
    Map<String, ConfigValues> children(String key) {
        Map<String, ConfigValues> children = new LinkedHashMap<>();
        ConfigValues parent = child(key);

        parent.values.keySet().forEach(name -> children.put(String.valueOf(name), parent.child(String.valueOf(name))));

        return children;
    }

    /** 값이 문자열 목록인 객체. 순서를 지킨다. */
    Map<String, List<String>> listMap(String key) {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        ConfigValues parent = child(key);

        parent.values.keySet().forEach(name -> lists.put(String.valueOf(name), parent.strings(String.valueOf(name))));

        return lists;
    }

    File baseDir() {
        return baseDir;
    }

    private String name(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private ToolingException invalid(String key, String expected) {
        return new ToolingException("Setting '" + name(key) + "' must be " + expected + ", got " + values.get(key));
    }
}
