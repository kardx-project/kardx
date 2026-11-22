package io.github.kardx.extractor;

import io.swagger.v3.oas.annotations.media.Schema;

/** record component 에 Swagger {@code @Schema} 설명을 붙인 Java record. */
public record SchemaJavaRecord(@Schema(description = "record 스키마 설명") String code) {
}
