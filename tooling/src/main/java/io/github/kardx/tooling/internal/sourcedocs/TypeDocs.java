package io.github.kardx.tooling.internal.sourcedocs;

import java.util.LinkedHashMap;
import java.util.Map;

/** 타입 하나의 설명, 속성 설명, enum 상수 설명. 같은 타입을 여러 번 만나면 먼저 들어온 값이 남는다. */
final class TypeDocs {
    private static final String DESCRIPTION = "description";

    private String description;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Map<String, DocPrecedence> precedences = new LinkedHashMap<>();
    private final Map<String, String> enumConstants = new LinkedHashMap<>();

    void describe(String text) {
        if (description == null && !text.isEmpty()) {
            description = text;
        }
    }

    /**
     * 타입 문서 주석: 본문은 타입 설명, {@code @property x} 는 속성 설명, {@code paramTags} 이면 {@code @param x} 도 속성
     * 설명(가장 낮은 우선 순위)이 된다.
     */
    void document(DocComment doc, boolean paramTags) {
        describe(doc.description());
        doc.tagged(DocCommentParser.PROPERTY).forEach((name, text) -> property(name, text, DocPrecedence.PROPERTY_TAG));

        if (paramTags) {
            parameterTags(doc);
        }
    }

    /** 생성자·record 문서의 {@code @param x}. 타입 파라미터({@code <T>})는 뺀다. */
    void parameterTags(DocComment doc) {
        doc.tagged(DocCommentParser.PARAM).forEach((name, text) -> {
            if (!name.startsWith("<")) {
                property(name, text, DocPrecedence.PARAM_TAG);
            }
        });
    }

    /** 속성 설명. 이미 있는 값보다 우선 순위가 높을 때만 바꾼다. */
    void property(String name, String text, DocPrecedence precedence) {
        DocPrecedence existing = precedences.get(name);

        if (text.isEmpty() || existing != null && existing.compareTo(precedence) >= 0) {
            return;
        }

        properties.put(name, text);
        precedences.put(name, precedence);
    }

    void enumConstant(String name, String text) {
        if (!text.isEmpty()) {
            enumConstants.putIfAbsent(name, text);
        }
    }

    /** 빈 값과 {@code options} 에서 끈 항목은 뺀다. 적을 것이 없으면 빈 맵. */
    Map<String, Object> toJson(SourceDocsOptions options) {
        Map<String, Object> json = new LinkedHashMap<>();

        if (options.schemaDescriptions() && description != null) {
            json.put(DESCRIPTION, description);
        }

        if (options.fieldDocs() && !properties.isEmpty()) {
            json.put("properties", properties);
        }

        if (options.enumDocs() && !enumConstants.isEmpty()) {
            json.put("enumConstants", enumConstants);
        }

        return json;
    }
}
