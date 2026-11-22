package io.github.kardx.tooling.internal.sourcedocs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 한 클래스의 같은 이름 메서드(오버로드 포함)의 문서. 키를 이렇게 정한다.
 * <ul>
 *   <li>모든 선언이 같은 문서를 가지면 {@code fqcn#name} 하나</li>
 *   <li>아니면 파라미터 수마다 {@code fqcn#name/<수>}. 같은 수의 선언끼리 문서가 다르거나 문서 없는 선언이 섞이면 그 수는 뺀다</li>
 * </ul>
 * "같은 문서"는 JSON 에 적을 내용({@link SourceDocsOptions} 가 켠 본문·{@code @param})이 같다는 뜻이다.
 */
final class MethodDocs {
    private static final String SUMMARY = "summary";
    private static final String DESCRIPTION = "description";
    private static final String PARAMS = "params";

    private final List<Overload> overloads = new ArrayList<>();

    private record Overload(int arity, DocComment doc) {
    }

    /** 메서드 하나에서 JSON 에 적을 내용. 본문을 넣지 않으면 summary·description 은 빈 문자열, params 는 빈 맵이다. */
    private record Entry(String summary, String description, Map<String, String> params) {
    }

    /** @param doc 문서가 없으면 null (오버로드 판단에만 쓴다) */
    void add(int arity, DocComment doc) {
        overloads.add(new Overload(arity, doc));
    }

    /**
     * {@code key}({@code fqcn#name}) 와 그 아래 키 → {@code {summary, description, params}}. 적을 내용이 없는 키는 뺀다.
     *
     * @param options summary·description({@code methodDocs})과 {@code params}({@code paramDocs}) 중 적을 것
     */
    void writeTo(String key, SourceDocsOptions options, Map<String, Object> methods) {
        List<Entry> entries = new ArrayList<>();

        overloads.forEach(overload -> entries.add(entry(overload.doc(), options)));

        Entry shared = sameEntry(entries);

        if (shared != null) {
            methods.put(key, json(shared));

            return;
        }

        Map<Integer, List<Entry>> byArity = new TreeMap<>();

        for (int i = 0; i < overloads.size(); i++) {
            byArity.computeIfAbsent(overloads.get(i).arity(), a -> new ArrayList<>()).add(entries.get(i));
        }

        byArity.forEach((arity, group) -> {
            Entry entry = sameEntry(group);

            if (entry != null) {
                methods.put(key + "/" + arity, json(entry));
            }
        });
    }

    /** 적을 내용이 없으면 null. */
    private static Entry entry(DocComment doc, SourceDocsOptions options) {
        if (doc == null) {
            return null;
        }

        Map<String, String> params = options.paramDocs() ? params(doc) : Map.of();
        boolean text = options.methodDocs() && doc.hasText();

        if (!text && params.isEmpty()) {
            return null;
        }

        return text ? new Entry(doc.summary(), doc.description(), params) : new Entry("", "", params);
    }

    /** {@code @param <이름>} → 글 (주석 순서). 타입 파라미터({@code @param <T>})는 뺀다. */
    private static Map<String, String> params(DocComment doc) {
        Map<String, String> params = new LinkedHashMap<>();

        doc.tagged(DocCommentParser.PARAM).forEach((name, text) -> {
            if (!name.startsWith("<")) {
                params.put(name, text);
            }
        });

        return params;
    }

    /** 모든 선언이 적을 내용이 있고 그 내용이 같으면 그 내용. */
    private static Entry sameEntry(List<Entry> group) {
        Entry first = group.get(0);

        for (Entry entry : group) {
            if (entry == null || !Objects.equals(entry, first)) {
                return null;
            }
        }

        return first;
    }

    private static Map<String, Object> json(Entry entry) {
        Map<String, Object> json = new LinkedHashMap<>();

        if (!entry.description().isEmpty()) {
            json.put(SUMMARY, entry.summary());
            json.put(DESCRIPTION, entry.description());
        }

        if (!entry.params().isEmpty()) {
            json.put(PARAMS, entry.params());
        }

        return json;
    }
}
