package io.github.kardx.dsl

import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.internal.reflect.enumJsonValues
import org.springframework.restdocs.request.ParameterDescriptor
import org.springframework.restdocs.request.RequestDocumentation
import kotlin.reflect.KClass

/**
 * 경로 변수 / 쿼리 / 폼 파라미터 DSL.
 *
 * ```kotlin
 * "id" pathMeans "사용자 ID"
 * "page" queryMeans "페이지 번호" isOptional true withDefaultValue "0"
 * "name" formMeans "이름" constraint "2자 이상" example "홍길동"
 * "id" pathMeans "사용자 ID" ofType Long::class    // io.github.kardx.extractor.ofType
 * ```
 *
 * 수식어(`isOptional`, `example`, `constraint` 등)는 이 파라미터를 바꾸지 않고 디스크립터까지 복사한 새 파라미터를 돌려준다.
 * 그래서 `val` 상수로 빼서 여러 테스트가 같이 써도, 한 테스트에서 붙인 수식어는 그 테스트에만 적용된다.
 * ([descriptor] 를 직접 바꾸면 공유한 상수도 바뀐다. 수식어를 쓴다.)
 *
 * ```kotlin
 * val PAGE = "page" queryMeans "페이지 번호"
 * queryParameters(PAGE isOptional true) // 이 테스트에만 optional. PAGE 는 그대로 필수다
 * ```
 */
public class RestDocsParam(public val descriptor: ParameterDescriptor) {
    /** 파라미터 이름 (`descriptor.name`). 추출한 목록을 고를 때 쓴다. `parametersOf<T>().filter { it.name != "debug" }` */
    public val name: String get() = descriptor.name

    /** true 이면 [optional] 과 같다. false 는 아무것도 바꾸지 않는다 (이미 optional 이면 그대로 둔다). */
    public infix fun isOptional(value: Boolean): RestDocsParam = if (value) optional() else this

    /**
     * 없어도 되는 항목으로 표시한다. 테스트 요청에 없어도 실패하지 않는다.
     * AsciiDoc 표의 Required 열이 `false` 가 되고, OpenAPI 파라미터의 `required` 가 false 가 된다.
     */
    public fun optional(): RestDocsParam = modified { it.optional() }

    /**
     * 제약조건 설명. AsciiDoc 표의 Constraints 열에 그대로 나가고, OpenAPI(Gradle 플러그인) 파라미터 `description` 뒤에
     * `Constraints: ...`(`language = KO` 면 `제약: ...`) 으로 붙는다. [io.github.kardx.extractor.parametersOf] 가 Bean Validation 어노테이션에서 채운 표 문구는
     * 덮어쓰고, 어노테이션에서 만든 스키마 키워드(`minimum` …)는 그대로 둔다. `"page" queryMeans "페이지 번호" constraint "0 이상"`
     */
    public infix fun constraint(value: String): RestDocsParam = modified {
        it.attributes[CONSTRAINTS_ATTRIBUTE] = value
        it.attributes[CONSTRAINT_NOTE_ATTRIBUTE] = value
    }

    /**
     * 예시 값. AsciiDoc 표의 Description 칸에서 설명 아래 `Example: ...` 줄로 나가고, OpenAPI(Gradle 플러그인) 파라미터의
     * `example` 이 된다 (Swagger UI 입력 칸의 기본값). 검증에는 영향이 없다. `"keyword" queryMeans "검색어" example "노트북"`
     */
    public infix fun example(value: String): RestDocsParam = modified { it.attributes[EXAMPLE_ATTRIBUTE] = value }

    /**
     * 가능한 값을 Enum 으로 정한다. 값은 JSON 에 실제로 나가는 값이다 (`@JsonValue`, 상수의 `@JsonProperty`, 없으면 상수 이름).
     * OpenAPI 에 `enum` 으로 나가 Swagger UI 에서 드롭다운이 된다.
     * `"status" queryMeans "상태" enumOf TodoStatus::class`
     */
    public infix fun enumOf(type: KClass<out Enum<*>>): RestDocsParam =
        modified { it.attributes[ENUM_VALUES_ATTRIBUTE] = enumJsonValues(type.java) }

    /**
     * 기본값. AsciiDoc 표의 Description 칸에서 설명 아래 `Default: ...` 줄로 나가고, OpenAPI 파라미터 스키마의 `default` 가
     * 된다 ([io.github.kardx.extractor.ofType] 이나 [io.github.kardx.extractor.parametersOf] 로 정한 타입에 맞춰
     * 숫자·불리언으로 바꾼다).
     * 보통 [optional] 과 함께 쓴다. `"size" queryMeans "페이지 크기" ofType Int::class isOptional true withDefaultValue "20"`
     */
    public infix fun withDefaultValue(value: String): RestDocsParam =
        modified { it.attributes[DEFAULT_VALUE_ATTRIBUTE] = value }

    /**
     * 더 이상 쓰지 않는 파라미터로 표시한다. AsciiDoc 표의 Description 칸 맨 앞에 `Deprecated` 줄이 생기고,
     * OpenAPI(Gradle 플러그인) 파라미터에 `deprecated: true` 가 붙는다. 검증에는 영향이 없다.
     * [io.github.kardx.extractor.parametersOf] 는 `@Deprecated` 프로퍼티에 자동으로 붙인다.
     * `"sort" queryMeans "정렬" deprecated()`
     */
    public fun deprecated(): RestDocsParam = modified { it.attributes.markDeprecated(null) }

    /** [deprecated] 에 이유·대안을 붙인다. 표에 `Deprecated: <note>` 로 나간다. `... deprecated "order 를 쓰세요"` */
    public infix fun deprecated(note: String): RestDocsParam = modified { it.attributes.markDeprecated(note) }

    /** 디스크립터까지 복사한 파라미터에 [change] 를 적용한다. 이 파라미터는 그대로다. */
    internal inline fun modified(change: (ParameterDescriptor) -> Unit): RestDocsParam =
        copied().also { change(it.descriptor) }
}

/**
 * 경로 변수. [io.github.kardx.dsl.DocumentationBuilder.pathParameters] 에 넘긴다.
 * URL 템플릿(`get("/api/users/{id}", 1L)`)에 없는 이름이면 테스트가 실패한다. `"id" pathMeans "사용자 ID"`
 */
public infix fun String.pathMeans(description: String): RestDocsParam = parameter(this, description)

/**
 * 쿼리 파라미터. [io.github.kardx.dsl.DocumentationBuilder.queryParameters] 에 넘긴다.
 * `"page" queryMeans "페이지 번호" isOptional true`
 */
public infix fun String.queryMeans(description: String): RestDocsParam = parameter(this, description)

/**
 * `application/x-www-form-urlencoded` 폼 파라미터. [io.github.kardx.dsl.DocumentationBuilder.formParameters] 에 넘긴다.
 * `"name" formMeans "이름"`
 */
public infix fun String.formMeans(description: String): RestDocsParam = parameter(this, description)

/** 설명을 핸들러의 `@param` 에서 가져오는 경로 변수. `"id" pathMeans FromParamTag isOptional true` */
@Suppress("UnusedParameter") // FromParamTag 는 오버로드를 고르는 표식이다
@ExperimentalRestDocsApi
public infix fun String.pathMeans(description: FromParamTag): RestDocsParam = undescribedParameter(this)

/** 설명을 핸들러의 `@param` 에서 가져오는 쿼리 파라미터. `"page" queryMeans FromParamTag isOptional true example "0"` */
@Suppress("UnusedParameter") // FromParamTag 는 오버로드를 고르는 표식이다
@ExperimentalRestDocsApi
public infix fun String.queryMeans(description: FromParamTag): RestDocsParam = undescribedParameter(this)

/** 설명을 핸들러의 `@param` 에서 가져오는 폼 파라미터. `"nickname" formMeans FromParamTag example "evan"` */
@Suppress("UnusedParameter") // FromParamTag 는 오버로드를 고르는 표식이다
@ExperimentalRestDocsApi
public infix fun String.formMeans(description: FromParamTag): RestDocsParam = undescribedParameter(this)

private fun parameter(name: String, description: String) = RestDocsParam(
    RequestDocumentation.parameterWithName(name)
        .description(description),
)

/** 설명 없이 이름만 적은 파라미터. 설명은 핸들러의 `@param` 에서 채운다 ([FromParamTag]). */
internal fun undescribedParameter(name: String): RestDocsParam =
    RestDocsParam(RequestDocumentation.parameterWithName(name))

/** 디스크립터까지 새로 만든 복사본. 원본을 바꾸지 않고 수식어를 더할 때 쓴다. */
internal fun RestDocsParam.copied(): RestDocsParam {
    val source = descriptor
    val copy = RequestDocumentation.parameterWithName(source.name)
        .description(source.description)

    copy.attributes.putAll(source.attributes)

    if (source.isOptional) copy.optional()

    if (source.isIgnored) copy.ignored()

    return RestDocsParam(copy)
}
