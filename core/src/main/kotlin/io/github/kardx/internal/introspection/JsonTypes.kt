package io.github.kardx.internal.introspection

/** JSON Schema `type` 값. 추출기가 만드는 스키마 조각([TypeSchemas], [ConstraintSchemas])이 쓴다. */
internal object JsonTypes {
    const val STRING: String = "string"
    const val INTEGER: String = "integer"
    const val NUMBER: String = "number"
    const val BOOLEAN: String = "boolean"
    const val ARRAY: String = "array"
    const val OBJECT: String = "object"
}
