package io.github.kardx.extractor;

import io.github.kardx.annotation.Masked;

/** record component 에 마스킹 표시를 붙인 Java record. */
public record MaskedJavaRecord(@Masked("뒤 4자리") String phone, String name) {
}
