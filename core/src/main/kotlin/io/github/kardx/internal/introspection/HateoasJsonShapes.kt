package io.github.kardx.internal.introspection

import io.github.kardx.internal.i18n.DocsText

// Spring HATEOAS 모델이 HAL(`application/hal+json`) 로 나가는 실제 모양. 추출기는 HATEOAS 타입을 이 클래스로 바꿔 읽는다
// ([HateoasTypes]). 프로퍼티 이름 `links`·`embedded` 는 [HateoasTypes.adjust] 가 `_links`·`_embedded` 로 바꾼다
// (Kotlin 프로퍼티 이름은 `_` 로 시작할 수 없게 검사한다). 값을 담지 않으므로 인스턴스는 만들지 않는다.

/**
 * `EntityModel<T>`. 내용([content])의 프로퍼티가 같은 수준에 펼쳐지고(`@JsonUnwrapped`) `_links` 가 붙는다.
 * 링크가 없으면 `_links` 는 빠진다 (`@JsonInclude(NON_EMPTY)`).
 */
internal class HalEntityJson<T>(val content: T, @BuiltInDescription(DocsText.HAL_LINKS) val links: Map<String, Any>?)

/**
 * `CollectionModel<T>`. [E] 는 원소의 관계 이름을 아는 [HalEmbeddedJson], 원소 타입을 모르면 키가 정해지지 않은 맵이다.
 * 원소가 없으면 `_embedded` 가 빠진다.
 */
internal class HalCollectionJson<E>(
    @BuiltInDescription(DocsText.HAL_EMBEDDED) val embedded: E?,
    @BuiltInDescription(DocsText.HAL_LINKS) val links: Map<String, Any>?,
)

/** `PagedModel<T>`. [HalCollectionJson] 에 페이지 정보 `page` 가 더해진다. */
internal class HalPagedJson<E>(
    @BuiltInDescription(DocsText.HAL_EMBEDDED) val embedded: E?,
    @BuiltInDescription(DocsText.HAL_LINKS) val links: Map<String, Any>?,
    @BuiltInDescription(DocsText.PAGE_METADATA) val page: HalPageMetadataJson?,
)

/** `_embedded` 객체. 원소 목록 하나가 관계 이름(`bookList`, `@Relation(collectionRelation)`) 키로 들어간다. */
internal class HalEmbeddedJson<T>(@BuiltInDescription(DocsText.HAL_EMBEDDED_ITEMS) val items: List<T>)

/** `PagedModel.PageMetadata`. 키는 `@JsonProperty` 로 정해져 앱의 이름 규칙을 따르지 않는다. */
internal class HalPageMetadataJson(
    @BuiltInDescription(DocsText.PAGE_SIZE) val size: Long,
    @BuiltInDescription(DocsText.PAGE_TOTAL_ELEMENTS) val totalElements: Long,
    @BuiltInDescription(DocsText.PAGE_TOTAL_PAGES) val totalPages: Long,
    @BuiltInDescription(DocsText.PAGE_NUMBER) val number: Long,
)

/** `RepresentationModel` 을 상속한 클래스에 붙는 `_links`. */
internal class HalLinksJson(@BuiltInDescription(DocsText.HAL_LINKS) val links: Map<String, Any>?)
