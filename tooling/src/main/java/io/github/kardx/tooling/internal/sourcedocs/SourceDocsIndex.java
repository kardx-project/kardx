package io.github.kardx.tooling.internal.sourcedocs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 스캔 결과: 타입(JVM binary name)별 문서와 메서드({@code fqcn#name})별 문서. {@link #toJson()} 이 core 가 읽는 JSON
 * (version {@value #VERSION})을 만든다. 키는 이름순이라 같은 소스면 같은 파일이 나온다.
 */
public final class SourceDocsIndex {
    /** JSON 형식 버전. core 의 읽는 쪽과 같이 바꾼다. */
    public static final int VERSION = 1;

    private final Map<String, TypeDocs> types = new TreeMap<>();
    private final Map<String, MethodDocs> methods = new TreeMap<>();
    private final SourceDocsOptions options;

    /**
     * 빈 색인을 만든다.
     *
     * @param options 색인에 적을 항목. 끈 항목은 {@link #toJson()} 에서 빠지고, 적을 것이 없는 타입·메서드도 빠진다
     */
    public SourceDocsIndex(SourceDocsOptions options) {
        this.options = options;
    }

    TypeDocs type(String binaryName) {
        return types.computeIfAbsent(binaryName, name -> new TypeDocs());
    }

    /** @param doc 문서가 없으면 null. 오버로드 판단에 쓰므로 문서 없는 메서드도 넣는다 */
    void method(String owner, String name, int arity, DocComment doc) {
        methods.computeIfAbsent(owner + "#" + name, key -> new MethodDocs()).add(arity, doc);
    }

    /** 색인에 적을 문서가 있는 타입 수. */
    public int typeCount() {
        return (int) types.values()
            .stream()
            .filter(type -> !type.toJson(options).isEmpty())
            .count();
    }

    /** {@code {"version": 1, "types": {…}, "methods": {…}}}. 빈 맵은 뺀다. */
    public Map<String, Object> toJson() {
        Map<String, Object> typeJson = new LinkedHashMap<>();
        Map<String, Object> methodJson = new LinkedHashMap<>();

        types.forEach((name, type) -> {
            Map<String, Object> json = type.toJson(options);

            if (!json.isEmpty()) {
                typeJson.put(name, json);
            }
        });
        methods.forEach((key, docs) -> docs.writeTo(key, options, methodJson));

        Map<String, Object> json = new LinkedHashMap<>();

        json.put("version", VERSION);

        if (!typeJson.isEmpty()) {
            json.put("types", typeJson);
        }

        if (!methodJson.isEmpty()) {
            json.put("methods", methodJson);
        }

        return json;
    }
}
