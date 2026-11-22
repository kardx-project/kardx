package io.github.kardx.internal.introspection

import io.github.kardx.internal.i18n.DocsText

// Spring Data 타입이 JSON 으로 나가는(또는 요청 파라미터로 들어오는) 실제 모양. 추출기는 Spring Data 타입을 이 클래스로 바꿔 읽는다.
// 프로퍼티 순서는 Jackson 2 의 출력 순서를 따른다. 값을 담지 않으므로 인스턴스는 만들지 않는다.

/** `PageImpl` 을 그대로 직렬화한 모양 (Spring Data 의 기본 `DIRECT` 모드). */
@Suppress("LongParameterList") // JSON 필드 목록을 그대로 옮긴 모양이다
internal class SpringDataPageJson<T>(
    @BuiltInDescription(DocsText.PAGE_CONTENT) val content: List<T>,
    @BuiltInDescription(DocsText.PAGE_PAGEABLE) val pageable: SpringDataPageableJson,
    @BuiltInDescription(DocsText.PAGE_TOTAL_ELEMENTS) val totalElements: Long,
    @BuiltInDescription(DocsText.PAGE_TOTAL_PAGES) val totalPages: Int,
    @BuiltInDescription(DocsText.PAGE_LAST) val last: Boolean,
    @BuiltInDescription(DocsText.PAGE_FIRST) val first: Boolean,
    @BuiltInDescription(DocsText.PAGE_SIZE) val size: Int,
    @BuiltInDescription(DocsText.PAGE_NUMBER) val number: Int,
    @BuiltInDescription(DocsText.PAGE_SORT) val sort: SpringDataSortJson,
    @BuiltInDescription(DocsText.PAGE_NUMBER_OF_ELEMENTS) val numberOfElements: Int,
    @BuiltInDescription(DocsText.PAGE_EMPTY) val empty: Boolean,
)

/** `SliceImpl` 을 그대로 직렬화한 모양. 전체 개수(`totalElements`, `totalPages`)가 없다. */
@Suppress("LongParameterList") // JSON 필드 목록을 그대로 옮긴 모양이다
internal class SpringDataSliceJson<T>(
    @BuiltInDescription(DocsText.PAGE_CONTENT) val content: List<T>,
    @BuiltInDescription(DocsText.PAGE_PAGEABLE) val pageable: SpringDataPageableJson,
    @BuiltInDescription(DocsText.PAGE_FIRST) val first: Boolean,
    @BuiltInDescription(DocsText.PAGE_LAST) val last: Boolean,
    @BuiltInDescription(DocsText.PAGE_SIZE) val size: Int,
    @BuiltInDescription(DocsText.PAGE_NUMBER) val number: Int,
    @BuiltInDescription(DocsText.PAGE_SORT) val sort: SpringDataSortJson,
    @BuiltInDescription(DocsText.PAGE_NUMBER_OF_ELEMENTS) val numberOfElements: Int,
    @BuiltInDescription(DocsText.PAGE_EMPTY) val empty: Boolean,
)

/**
 * `PageImpl` 을 `PagedModel` 로 바꿔 직렬화한 모양. `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`
 * 나 `spring.data.web.pageable.serialization-mode=via-dto` 일 때 Spring Data 가 이렇게 내보낸다.
 */
internal class SpringDataPagedJson<T>(
    @BuiltInDescription(DocsText.PAGE_CONTENT) val content: List<T>,
    @BuiltInDescription(DocsText.PAGE_METADATA) val page: SpringDataPageMetadataJson,
)

/** `PagedModel.PageMetadata`. */
internal class SpringDataPageMetadataJson(
    @BuiltInDescription(DocsText.PAGE_SIZE) val size: Long,
    @BuiltInDescription(DocsText.PAGE_NUMBER) val number: Long,
    @BuiltInDescription(DocsText.PAGE_TOTAL_ELEMENTS) val totalElements: Long,
    @BuiltInDescription(DocsText.PAGE_TOTAL_PAGES) val totalPages: Long,
)

/** `Sort` 를 그대로 직렬화한 모양. 정렬 기준(속성·방향)은 JSON 에 나가지 않는다. */
internal class SpringDataSortJson(
    @BuiltInDescription(DocsText.SORT_EMPTY) val empty: Boolean,
    @BuiltInDescription(DocsText.SORT_SORTED) val sorted: Boolean,
    @BuiltInDescription(DocsText.SORT_UNSORTED) val unsorted: Boolean,
)

/**
 * `PageRequest` 를 그대로 직렬화한 모양. 페이지 없이 만든 `PageImpl`(`Pageable.unpaged()`)은 Jackson 이
 * 직렬화하지 못하므로(`UnsupportedOperationException`) 응답의 `pageable` 은 항상 이 모양이다.
 */
internal class SpringDataPageableJson(
    @BuiltInDescription(DocsText.PAGE_NUMBER) val pageNumber: Int,
    @BuiltInDescription(DocsText.PAGE_SIZE) val pageSize: Int,
    @BuiltInDescription(DocsText.PAGE_SORT) val sort: SpringDataSortJson,
    @BuiltInDescription(DocsText.PAGEABLE_OFFSET) val offset: Long,
    @BuiltInDescription(DocsText.PAGEABLE_PAGED) val paged: Boolean,
    @BuiltInDescription(DocsText.PAGEABLE_UNPAGED) val unpaged: Boolean,
)

/** 컨트롤러의 `Pageable` 파라미터가 받는 쿼리 파라미터. 이름은 Spring Data 기본값이다. */
internal class SpringDataPageableParameters(
    @BuiltInDescription(DocsText.PAGE_NUMBER) val page: Int?,
    @BuiltInDescription(DocsText.PAGE_SIZE) val size: Int?,
    @BuiltInDescription(DocsText.SORT_PARAMETER) val sort: List<String>?,
)

/** 컨트롤러의 `Sort` 파라미터가 받는 쿼리 파라미터. */
internal class SpringDataSortParameters(@BuiltInDescription(DocsText.SORT_PARAMETER) val sort: List<String>?)
