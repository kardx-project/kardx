package io.github.kardx.extractor

import io.github.kardx.dsl.RestDocsParam
import io.github.kardx.internal.documentation.DescriptorTable

/**
 * 파라미터 목록이 문서에 무엇으로 나갈지 표로 보여 준다. 테스트를 돌리지 않고 `parametersOf<T>()` 의 추출 결과를 확인할 때 쓴다.
 * 열은 AsciiDoc `query-parameters` 표와 같다 (Parameter·Required·Description·Constraints).
 *
 * ```kotlin
 * println(parametersOf<ProductSearchCondition>().describe())
 * // | Parameter | Required | Description | Constraints |
 * // |-----------|----------|-------------|-------------|
 * // | keyword   | false    | 검색어         |             |
 * ```
 */
public fun List<RestDocsParam>.describe(): String = DescriptorTable.render(
    listOf("Parameter", "Required", "Description", "Constraints"),
    map { it.descriptor }
        .filterNot { it.isIgnored }
        .map { descriptor ->
            listOf(
                DescriptorTable.cell(descriptor.name),
                DescriptorTable.required(descriptor, descriptor.isOptional),
                DescriptorTable.description(descriptor),
                DescriptorTable.constraints(descriptor),
            )
        },
)

/** [describe] 표를 표준 출력에 찍고 목록을 그대로 돌려준다. `queryParameters(parametersOf<Condition>().printDescription())` */
public fun List<RestDocsParam>.printDescription(): List<RestDocsParam> = also { println(describe()) }
