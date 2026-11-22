package io.github.kardx.extractor;

import io.github.kardx.annotation.Description;

/** record component 에 설명을 붙인 Java record. */
public record DescribedJavaRecord(@Description("record 설명") String code) {
}
