package io.github.kardx.extractor;

import java.util.List;

/** 타입 파라미터가 있는 Java record. 제네릭 치환 테스트 픽스처. */
public record GenericRecord<T>(T data, List<T> items) {
}
