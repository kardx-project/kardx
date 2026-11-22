package io.github.kardx.dsl

import io.github.kardx.internal.provenance.FieldOrigin
import io.github.kardx.internal.provenance.FieldSource
import io.github.kardx.internal.reflect.enumJsonValues
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.payload.SubsectionDescriptor
import kotlin.reflect.KClass

/**
 * 요청/응답 본문 필드 DSL.
 *
 * ```kotlin
 * "nickname" type STRING means "닉네임" isOptional true constraint "2~10자" example "홍길동"
 * "metadata" subsection OBJECT means "자유 형식 메타데이터"
 * ```
 *
 * 수식어(`means`, `isOptional`, `example`, `constraint` 등)는 이 필드를 바꾸지 않고 디스크립터까지 복사한 새 필드를 돌려준다.
 * 그래서 `val` 상수로 빼서 여러 테스트가 같이 써도, 한 테스트에서 붙인 수식어는 그 테스트에만 적용된다.
 * ([descriptor] 를 직접 바꾸면 공유한 상수도 바뀐다. 수식어를 쓴다.)
 *
 * ```kotlin
 * val NICKNAME = "nickname" type STRING means "닉네임"
 * requestFields(NICKNAME isOptional true) // 이 테스트에만 optional. NICKNAME 은 그대로 필수다
 * ```
 */
public class RestDocsField(public val descriptor: FieldDescriptor) {
    /** 이 필드를 만든 추출 (OpenAPI 스키마 이름 기본값). 직접 쓴 필드는 null */
    @Volatile
    internal var origin: FieldOrigin? = null

    /** 이 필드를 추출한 타입과 경로 (다형 프로퍼티 위치). 직접 쓴 필드는 null */
    @Volatile
    internal var source: FieldSource? = null

    /**
     * 필드 설명. AsciiDoc 표의 Description 열, OpenAPI 스키마 속성의 `description`,
     * AsyncAPI 페이로드 스키마의 `description` 에 들어간다. 다시 부르면 덮어쓴다.
     */
    public infix fun means(value: String): RestDocsField = modified { it.description(value) }

    /** true 이면 [optional] 과 같다. false 는 아무것도 바꾸지 않는다 (이미 optional 이면 그대로 둔다). */
    public infix fun isOptional(value: Boolean): RestDocsField = if (value) optional() else this

    /**
     * 없어도 되는 항목으로 표시한다. 테스트 요청·응답에 없어도 실패하지 않는다.
     * AsciiDoc 표의 Required 열이 `false` 가 되고, OpenAPI·AsyncAPI 스키마의 `required` 목록에서 빠진다.
     */
    public fun optional(): RestDocsField = modified { it.optional() }

    /** true 이면 [ignored] 와 같다. false 는 아무것도 바꾸지 않는다 (이미 ignored 이면 그대로 둔다). */
    public infix fun isIgnored(value: Boolean): RestDocsField = if (value) ignored() else this

    /** 문서에서 뺀다. 필드 검증에서는 문서화한 것으로 보아 "문서화하지 않은 필드" 로 실패하지 않는다. */
    public fun ignored(): RestDocsField = modified { it.ignored() }

    /**
     * 제약조건 설명. AsciiDoc 표의 Constraints 열에 그대로 나가고, OpenAPI(Gradle 플러그인)·AsyncAPI 스키마의 `description`
     * 뒤에 `Constraints: ...`(`language = KO` 면 `제약: ...`) 으로 붙는다. [io.github.kardx.extractor.requestFieldsOf] 등이 Bean Validation 어노테이션에서 채운
     * 표 문구는 덮어쓰고, 어노테이션에서 만든 스키마 키워드(`minLength`, `maximum` …)는 그대로 둔다.
     * `"nickname" type STRING means "닉네임" constraint "2~10자"`
     */
    public infix fun constraint(value: String): RestDocsField = modified {
        it.attributes[CONSTRAINTS_ATTRIBUTE] = value
        it.attributes[CONSTRAINT_NOTE_ATTRIBUTE] = value
    }

    /**
     * 예시 값. AsciiDoc 표의 Description 칸에서 설명 아래 `Example: ...` 줄로 나가고, OpenAPI(Gradle 플러그인) 스키마의
     * `example`, AsyncAPI 스키마의 `examples` 가 된다. 값은 스키마 타입에 맞춰 바꾼다 (`integer` 면 숫자, `boolean` 이면 불리언).
     * 필드 검증에는 영향이 없다. `"nickname" type STRING means "닉네임" example "홍길동"`
     */
    public infix fun example(value: String): RestDocsField = modified { it.attributes[EXAMPLE_ATTRIBUTE] = value }

    /**
     * 가능한 값을 Enum 으로 정한다. OpenAPI·AsyncAPI 에 `enum` 으로 나가 Swagger UI 에서 드롭다운이 된다.
     * 값은 JSON 에 실제로 나가는 값이다 (`@JsonValue`, 상수의 `@JsonProperty`, 없으면 상수 이름).
     * `"status" type ENUM means "상태" enumOf TodoStatus::class`
     *
     * 필드 타입은 바꾸지 않는다. AsciiDoc 표의 Type 열은 `String` 이고 REST Docs 가 실제 값이 문자열인지 검증한다.
     * epages 가 enum 스키마를 만들려면 필드 타입이 `enum` 이어야 해서, OpenAPI 조각에 넘길 때만 타입을 바꾼다.
     */
    public infix fun enumOf(type: KClass<out Enum<*>>): RestDocsField =
        modified { it.attributes[ENUM_VALUES_ATTRIBUTE] = enumJsonValues(type.java) }

    /**
     * 기본값. AsciiDoc 표의 Description 칸에서 설명 아래 `Default: ...` 줄로 나가고, OpenAPI(Gradle 플러그인)·AsyncAPI 스키마의
     * `default` 가 된다 (스키마 타입에 맞춰 숫자·불리언으로 바꾼다). 검증에는 영향이 없다.
     * `"size" type NUMBER means "페이지 크기" isOptional true withDefaultValue "20"`
     */
    public infix fun withDefaultValue(value: String): RestDocsField =
        modified { it.attributes[DEFAULT_VALUE_ATTRIBUTE] = value }

    /**
     * 더 이상 쓰지 않는 필드로 표시한다. AsciiDoc 표의 Description 칸 맨 앞에 `Deprecated` 줄이 생기고,
     * OpenAPI(Gradle 플러그인)·AsyncAPI 스키마의 그 속성에 `deprecated: true` 가 붙는다. 검증에는 영향이 없다.
     * [io.github.kardx.extractor.responseFieldsOf] 등은 `@Deprecated` 프로퍼티에 자동으로 붙인다.
     * `"nick" type STRING means "닉네임" deprecated()`
     */
    public fun deprecated(): RestDocsField = modified { it.attributes.markDeprecated(null) }

    /** [deprecated] 에 이유·대안을 붙인다. 표에 `Deprecated: <note>` 로 나간다. `... deprecated "nickname 을 쓰세요"` */
    public infix fun deprecated(note: String): RestDocsField = modified { it.attributes.markDeprecated(note) }

    /**
     * 값이 마스킹되어 나가는 필드로 표시한다. 값을 바꾸지 않는 문서 표시다 (헤더 값을 가리는 헤더 마스킹과 다르다).
     * AsciiDoc 표의 Description 칸 맨 앞(`Deprecated` 다음)에 `Masked` 줄이 생기고, OpenAPI(Gradle 플러그인)·AsyncAPI 스키마의
     * 그 속성에 `x-masked: true` 와 설명 끝 `Masked` 가 붙는다. 검증에는 영향이 없다.
     * [io.github.kardx.extractor.responseFieldsOf] 등은 `@Masked` 프로퍼티에 자동으로 붙인다.
     * `"phone" type STRING means "전화번호" masked()`
     */
    public fun masked(): RestDocsField = modified { it.attributes.markMasked(null) }

    /**
     * [masked] 에 마스킹 규칙 설명을 붙인다. 표에 `Masked: <rule>`, 스키마에 `x-masked-rule` 로 나간다.
     * `"name" type STRING means "이름" masked "가운데 글자를 *"`
     */
    public infix fun masked(rule: String): RestDocsField = modified { it.attributes.markMasked(rule) }

    /** 디스크립터까지 복사한 필드에 [change] 를 적용한다. 이 필드는 그대로이고, 추출 출처(스키마 이름 기본값, 다형 프로퍼티 위치)는 이어받는다. */
    private inline fun modified(change: (FieldDescriptor) -> Unit): RestDocsField = copied().also { copy ->
        change(copy.descriptor)
        copy.origin = origin
        copy.source = source
    }
}

/**
 * 필드 DSL 의 시작점. `"userId" type NUMBER means "사용자 ID"`
 *
 * 타입은 AsciiDoc 표의 Type 열과 OpenAPI·AsyncAPI 스키마의 `type` 이 된다. [ENUM]·[DATE]·[DATETIME] 은 `STRING` 이고,
 * [DATE]·[DATETIME] 은 OpenAPI·AsyncAPI 에 `format: date`·`format: date-time` 이 붙는다.
 * 실제 본문의 JSON 타입과 다르면 테스트가 실패한다 ([VARIES] 는 검사하지 않는다).
 */
public infix fun String.type(docsFieldType: DocsFieldType): RestDocsField = RestDocsField(
    PayloadDocumentation.fieldWithPath(this)
        .type(docsFieldType.toJsonFieldType())
        .description("")
        .apply { docsFieldType.schemaFormat()?.let { attributes[SCHEMA_ATTRIBUTE] = it } },
)

/**
 * 하위 필드를 검증하지 않는 subsection 필드. 키가 정해지지 않은 객체나 문서화하지 않을 큰 하위 구조에 쓴다.
 * `"metadata" subsection OBJECT means "자유 형식"`
 */
public infix fun String.subsection(docsFieldType: DocsFieldType): RestDocsField = RestDocsField(
    PayloadDocumentation.subsectionWithPath(this)
        .type(docsFieldType.toJsonFieldType())
        .description(""),
)

/**
 * 방금 만든 필드(추출 결과)에 Enum 값 목록을 넣는다. 복사하지 않고 이 필드를 바꾼다. 필드 타입은 그대로다. epages 는 필드 타입이 `enum` 일 때 이 목록으로 스키마를 만들므로
 * OpenAPI 조각에 넘기는 디스크립터만 타입을 바꾼다 (`io.github.kardx.internal.openapi.OpenApiDescriptors`).
 */
internal fun RestDocsField.enumValues(values: List<String>): RestDocsField = apply {
    descriptor.attributes[ENUM_VALUES_ATTRIBUTE] = values
}

/** [path] 의 필드이거나 그 아래(`path.x`, `path[]`, `path[].x`) 필드인지. */
internal fun RestDocsField.isUnder(path: String): Boolean {
    val own = descriptor.path

    return own == path || own.startsWith("$path.") || own.startsWith("$path[")
}

/** 디스크립터까지 새로 만든 복사본. 원본을 바꾸지 않고 수식어를 더할 때 쓴다. */
internal fun RestDocsField.copied(): RestDocsField {
    val source = descriptor
    val copy = if (source is SubsectionDescriptor) {
        PayloadDocumentation.subsectionWithPath(source.path)
    } else {
        PayloadDocumentation.fieldWithPath(source.path)
    }

    copy.type(source.type)
        .description(source.description)
        .attributes
        .putAll(source.attributes)

    if (source.isOptional) copy.optional()

    if (source.isIgnored) copy.ignored()

    return RestDocsField(copy)
}

/**
 * 방금 만든 배열 필드(추출 결과)에 원소의 Enum 값 목록을 넣는다. 복사하지 않고 이 필드를 바꾼다. 필드 타입은 배열 그대로라 REST Docs 가 배열인지 검증하고,
 * epages 는 `itemsType` 이 `enum` 이면 `items` 를 enum 스키마로 만든다.
 */
internal fun RestDocsField.arrayEnumValues(values: List<String>): RestDocsField = apply {
    descriptor.attributes[ITEMS_TYPE_ATTRIBUTE] = ENUM_FIELD_TYPE
    descriptor.attributes[ENUM_VALUES_ATTRIBUTE] = values
}
