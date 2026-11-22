package io.github.kardx.extractor

import io.github.kardx.dsl.RestDocsField
import io.github.kardx.internal.documentation.DescriptorTable
import org.springframework.restdocs.payload.SubsectionDescriptor

/**
 * 필드 목록이 문서에 무엇으로 나갈지 표로 보여 준다. 테스트를 돌리지 않고 `responseFieldsOf<T>()`,
 * `requestFieldsOf<T>()`, `wrappedResponseFieldsOf<T>()` 의 추출 결과나 직접 쓴 목록을 확인할 때 쓴다.
 * 열은 AsciiDoc `response-fields` 표와 같다 (Path·Type·Required·Description·Constraints). ignored 필드는 표에 없다.
 * 하위 필드를 검증하지 않는 subsection 필드는 Type 에 `(subsection)` 이 붙는다.
 *
 * ```kotlin
 * println(responseFieldsOf<UserResponse>().describe())
 * // | Path     | Type   | Required | Description        | Constraints |
 * // |----------|--------|----------|--------------------|-------------|
 * // | id       | Number | true     | 사용자 ID            |             |
 * // | nickname | String | false    | 닉네임 (Example: 홍길동) | 2~10자       |
 * ```
 */
public fun List<RestDocsField>.describe(): String = DescriptorTable.render(
    listOf("Path", "Type", "Required", "Description", "Constraints"),
    map { it.descriptor }
        .filterNot { it.isIgnored }
        .map { descriptor ->
            val subsection = if (descriptor is SubsectionDescriptor) " (subsection)" else ""

            listOf(
                DescriptorTable.cell(descriptor.path),
                "${descriptor.type}$subsection",
                DescriptorTable.required(descriptor, descriptor.isOptional),
                DescriptorTable.description(descriptor),
                DescriptorTable.constraints(descriptor),
            )
        },
)

/**
 * [describe] 표를 표준 출력에 찍고 목록을 그대로 돌려준다. 문서화 블록 안에서 바로 쓸 수 있다.
 * `responseFields(responseFieldsOf<UserResponse>().printDescription())`
 */
public fun List<RestDocsField>.printDescription(): List<RestDocsField> = also { println(describe()) }
