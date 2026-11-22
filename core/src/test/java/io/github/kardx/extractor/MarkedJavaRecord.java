package io.github.kardx.extractor;

/** {@code @NullMarked} 범위: 어노테이션 없는 타입은 null 이 아니고 {@code @Nullable} 만 null 일 수 있다. */
@NullMarked
public record MarkedJavaRecord(Long id, @Nullable String memo) {
}
