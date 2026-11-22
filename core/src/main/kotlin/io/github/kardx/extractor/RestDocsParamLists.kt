package io.github.kardx.extractor

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.dsl.RestDocsParam

/**
 * [names] 의 파라미터를 뺀 목록을 돌려준다. 원래 목록은 바꾸지 않는다. 추출한 목록에서 문서에 내지 않을 파라미터
 * (디버그용 플래그 등)를 빼거나 직접 다시 기술할 파라미터를 뺄 때 쓴다.
 *
 * ```kotlin
 * queryParameters(parametersOf<ProductSearchCondition>().except("debug"))
 * ```
 *
 * @throws IllegalArgumentException [names] 중에 목록에 없는 이름이 있을 때 (오타로 파라미터가 남지 않도록)
 */
public fun List<RestDocsParam>.except(vararg names: String): List<RestDocsParam> {
    requireKnown(names.toList(), "except")

    return filterNot { it.name in names }
}

/**
 * [names] 의 파라미터를 optional 로 바꾼 목록을 돌려준다. 원래 목록과 파라미터는 바꾸지 않고 바꿀 파라미터만 복사한다.
 * AsciiDoc 표의 Required 열이 `false` 가 되고 OpenAPI 파라미터의 `required` 가 false 가 된다.
 * 추출에서 필수로 본 파라미터(기본값 없는 non-null 프로퍼티)가 실제로는 없어도 될 때 쓴다.
 *
 * ```kotlin
 * queryParameters(parametersOf<ProductSearchCondition>().optionalAt("keyword", "status"))
 * ```
 *
 * @throws IllegalArgumentException [names] 중에 목록에 없는 이름이 있을 때
 */
public fun List<RestDocsParam>.optionalAt(vararg names: String): List<RestDocsParam> {
    requireKnown(names.toList(), "optionalAt")

    return map { param ->
        if (param.name in names) {
            param.optional()
        } else {
            param
        }
    }
}

private fun List<RestDocsParam>.requireKnown(names: List<String>, function: String) {
    val known = map { it.name }
        .toSet()
    val unknown = names.filterNot { it in known }

    require(unknown.isEmpty()) {
        RestDocsErrorCode.UNKNOWN_ITEM.message(
            "$function: no parameter named ${unknown.joinToString()}. Parameters: ${known.joinToString()}",
        )
    }
}
