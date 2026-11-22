package io.github.kardx.extractor;

/**
 * package-private 어노테이션과 반복 어노테이션을 붙인 Java record.
 * 반복 어노테이션은 첫 값이 비어 있어 둘째 값을 써야 한다.
 */
public record OddlyDocumentedJavaRecord(
    @PackagePrivateDoc("package-private 설명") String hidden,
    @RepeatedDoc("") @RepeatedDoc("반복 둘째") String repeated,
    @RepeatedDoc("반복 하나") String single
) {
}
