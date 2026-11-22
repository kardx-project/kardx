package io.github.kardx.openapi

import kotlin.reflect.KType

/**
 * 추출한 본문 타입 → OpenAPI 스키마 이름 규칙. [io.github.kardx.config.RestDocsSettings] 의 `openApi.schemaNameResolver` 로
 * 정한다. 정하지 않으면 [SchemaNames.of] (클래스 이름, 제네릭은 이어 붙임). 돌려준 이름은 그대로 쓴다.
 *
 * ```kotlin
 * openApi { schemaNameResolver = SchemaNameResolver { "Api" + SchemaNames.of(it.type) } }
 * ```
 */
public fun interface SchemaNameResolver {
    /** [input] 타입의 스키마 이름. */
    public fun resolve(input: SchemaNameInput): String
}

/**
 * [SchemaNameResolver] 가 받는 입력. 라이브러리가 만들어 넘기며, 앞으로 정보가 더해질 수 있다.
 *
 * @property type 이름을 붙일 타입 (`ApiResponse<TodoResponse>`). 래퍼를 떼는 설정(`schemaNamesWithoutWrapper`)이면 데이터 타입
 */
public class SchemaNameInput internal constructor(public val type: KType) {
    override fun toString(): String = "SchemaNameInput($type)"
}
