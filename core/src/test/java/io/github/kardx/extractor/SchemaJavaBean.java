package io.github.kardx.extractor;

import io.swagger.v3.oas.annotations.media.Schema;

/** 필드와 getter 에 Swagger {@code @Schema} 설명을 붙인 Java bean. */
public class SchemaJavaBean {
    @Schema(description = "필드 스키마 설명")
    private String onField;

    private String onGetter;

    public String getOnField() {
        return onField;
    }

    @Schema(description = "getter 스키마 설명")
    public String getOnGetter() {
        return onGetter;
    }
}
