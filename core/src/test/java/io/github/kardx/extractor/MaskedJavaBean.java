package io.github.kardx.extractor;

import io.github.kardx.annotation.Masked;

/** 필드와 getter 에 마스킹 표시를 붙인 Java bean. */
public class MaskedJavaBean {
    @Masked
    private String onField;
    private String onGetter;
    private String plain;

    public String getOnField() {
        return onField;
    }

    @Masked("getter 규칙")
    public String getOnGetter() {
        return onGetter;
    }

    public String getPlain() {
        return plain;
    }
}
