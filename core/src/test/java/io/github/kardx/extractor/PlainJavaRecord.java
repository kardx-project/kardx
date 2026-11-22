package io.github.kardx.extractor;

/** 어노테이션 없는 Java record. 참조 타입은 null 일 수 있고 원시 타입은 아니다. */
public record PlainJavaRecord(Long id, String memo, long count, @NonNull String name) {
}
