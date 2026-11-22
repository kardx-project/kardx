package io.github.kardx.dsl

/** 디스크립터 attributes 에 제약조건 설명을 저장하는 키. 라이브러리 스니펫 템플릿의 Constraints 컬럼이 읽는다. */
public const val CONSTRAINTS_ATTRIBUTE: String = "constraints"

/** Enum 상수 목록 키. epages(OpenAPI) 가 이 키를 읽어 `enum` 스키마를 만든다. */
internal const val ENUM_VALUES_ATTRIBUTE: String = "enumValues"

/**
 * 배열 원소 타입 키. epages 가 배열 필드의 `items` 스키마 타입으로 읽는다 (`enum` 이면 [ENUM_VALUES_ATTRIBUTE] 로 enum).
 * epages 가 아는 값(`string`, `number`, `boolean`, `enum`)만 넣는다. `array` 를 넣으면 epages 가 끝없이 재귀한다.
 */
internal const val ITEMS_TYPE_ATTRIBUTE: String = "itemsType"

/** epages 가 본문 필드를 enum 스키마로 만들 때 요구하는 필드 타입. OpenAPI 조각에 넘기는 디스크립터에만 쓴다. */
internal const val ENUM_FIELD_TYPE: String = "enum"

/** 예시 값 키. 스니펫 템플릿이 `{{example}}` 로 읽고, OpenAPI·AsyncAPI 의 `example` 이 된다. */
internal const val EXAMPLE_ATTRIBUTE: String = "example"

/** 기본값 키. 스니펫 템플릿이 `{{default}}` 로 읽고, OpenAPI·AsyncAPI 의 `default` 가 된다. */
internal const val DEFAULT_VALUE_ATTRIBUTE: String = "default"

/**
 * 타입·제약에서 정한 JSON Schema 조각 키 (`type`, `format`, `minLength`, `minimum`, `items` …).
 * Gradle 플러그인이 OpenAPI 스키마에, AsyncAPI 생성기가 페이로드 스키마에 합친다. `exclusiveMinimum` 은 OpenAPI 3.0 처럼 불리언이다.
 */
internal const val SCHEMA_ATTRIBUTE: String = "schema"

/** 스키마 키워드로 옮길 수 없는 제약 설명 키 (`constraint` 수식어, `@Past` 같은 제약). OpenAPI·AsyncAPI 설명 뒤에
 * `Constraints: ...`(한국어 설정이면 `제약: ...`) 으로 붙는다.
 */
internal const val CONSTRAINT_NOTE_ATTRIBUTE: String = "constraintNote"

/**
 * nullable 이거나 기본값이 있어 optional 이지만 `@NotNull`·`@NotBlank`·`@NotEmpty` 로 값이 꼭 있어야 하는 항목 표시.
 * 스니펫 템플릿의 Required 열이 `true` 가 되고 OpenAPI·AsyncAPI 의 `required` 에 들어간다. REST Docs 검증은 optional 그대로 둔다.
 */
internal const val REQUIRED_BY_CONSTRAINT_ATTRIBUTE: String = "requiredByConstraint"

/**
 * 부모가 optional 이라 REST Docs 에서는 optional 이지만, 부모 객체가 있으면 꼭 있는 하위 필드 표시 (`options[].name`).
 * OpenAPI·AsyncAPI 에서 부모 스키마의 `required` 에 넣고 `nullable` 을 뺀다. 표의 Required 열은 바꾸지 않는다.
 */
internal const val REQUIRED_IN_PARENT_ATTRIBUTE: String = "requiredInParent"

/**
 * optional 본문 필드의 값이 `null` 일 수 있는지 (`true`/`false`). 타입에서 추출한 필드에만 있다.
 * epages 는 optional 값 필드에만 `nullable: true` 를 붙이고(기본값이 있는 non-null `tags: List<String> = emptyList()` 포함),
 * 하위 필드가 있는 객체·배열(`errors: List<FieldError>?`)에는 붙이지 않는다. Gradle 플러그인이 이 값으로 `nullable` 을 맞춘다.
 */
internal const val NULLABLE_ATTRIBUTE: String = "nullable"

/**
 * 배열 본문 필드의 원소가 `null` 일 수 있다는 표시 (`List<Box?>`). epages 는 원소 스키마에 `nullable` 을 붙이지 않고,
 * `null` 원소를 통과시키려고 더한 ignored 줄(`boxes[]`)은 버리므로 배열 줄에 남긴다. Gradle 플러그인이 `items` 에 `nullable: true` 를 넣는다.
 */
internal const val NULLABLE_ITEMS_ATTRIBUTE: String = "nullableItems"

/**
 * 더 이상 쓰지 않는 항목 표시 (`true`). 스니펫 템플릿이 설명 앞에 `Deprecated` 를 쓰고, OpenAPI(Gradle 플러그인) 필드 스키마·
 * 파라미터와 AsyncAPI 스키마에 `deprecated: true` 가 된다. 추출기는 Kotlin·Java `@Deprecated` 프로퍼티에 넣는다.
 */
internal const val DEPRECATED_ATTRIBUTE: String = "deprecated"

/** [DEPRECATED_ATTRIBUTE] 의 이유·대안 (Kotlin `@Deprecated` 의 message, `deprecated("...")`). 표에 `Deprecated: ...` 로 나간다. */
internal const val DEPRECATION_NOTE_ATTRIBUTE: String = "deprecationNote"

/** [DEPRECATED_ATTRIBUTE] 와 이유를 넣는다. 빈 이유는 넣지 않는다. */
internal fun MutableMap<String, Any>.markDeprecated(note: String?) {
    put(DEPRECATED_ATTRIBUTE, true)
    note?.takeIf { it.isNotBlank() }
        ?.let { put(DEPRECATION_NOTE_ATTRIBUTE, it) }
}

/**
 * 값이 마스킹되어 나가는 필드 표시 (`true`). 값을 바꾸지 않는 문서 표시다. 스니펫 템플릿이 설명 앞에 `Masked` 를 쓰고
 * (`Deprecated` 다음), OpenAPI(Gradle 플러그인)·AsyncAPI 스키마 속성에 `x-masked: true` 와 설명 끝 `Masked: ...` 가 된다.
 * 추출기는 `@Masked` 와 설정의 마스킹 어노테이션이 붙은 프로퍼티에 넣는다.
 */
internal const val MASKED_ATTRIBUTE: String = "masked"

/** [MASKED_ATTRIBUTE] 의 규칙 설명 (`@Masked` 의 value, `masked("...")`). 표에 `Masked: ...`, 스키마에 `x-masked-rule` 로 나간다. */
internal const val MASKING_RULE_ATTRIBUTE: String = "maskingRule"

/** [MASKED_ATTRIBUTE] 와 규칙을 넣는다. 빈 규칙은 넣지 않는다. */
internal fun MutableMap<String, Any>.markMasked(rule: String?) {
    put(MASKED_ATTRIBUTE, true)
    rule?.takeIf { it.isNotBlank() }
        ?.let { put(MASKING_RULE_ATTRIBUTE, it) }
}
