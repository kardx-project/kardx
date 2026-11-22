package io.github.kardx.extractor
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.PropertyNameStrategy
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.support.verifyResponseFields
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.Link
import org.springframework.hateoas.PagedModel
import org.springframework.hateoas.RepresentationModel
import org.springframework.hateoas.mediatype.MessageResolver
import org.springframework.hateoas.mediatype.hal.CurieProvider
import org.springframework.hateoas.mediatype.hal.Jackson2HalModule
import org.springframework.hateoas.server.core.AnnotationLinkRelationProvider
import org.springframework.hateoas.server.core.DefaultLinkRelationProvider
import org.springframework.hateoas.server.core.DelegatingLinkRelationProvider
import org.springframework.hateoas.server.core.Relation

data class Book(val id: Long, val title: String, val publishedYear: Int)

@Relation(collectionRelation = "magazines")
data class Magazine(val id: Long, val issue: Int)

class BookModel(val id: Long, val title: String) : RepresentationModel<BookModel>()

data class Shelf(val name: String, val featured: EntityModel<Book>)

private val BOOK = Book(1, "Kotlin", 2024)
private val SELF = Link.of("/books/1")

/** Spring HATEOAS 가 앱에 등록하는 것과 같은 HAL 매퍼 (`@Relation` → 기본 규칙 `<타입>List`). */
private fun halMapper(base: ObjectMapper = ObjectMapper()): ObjectMapper = base.apply {
    registerModule(Jackson2HalModule())
    setHandlerInstantiator(
        Jackson2HalModule.HalHandlerInstantiator(
            DelegatingLinkRelationProvider(AnnotationLinkRelationProvider(), DefaultLinkRelationProvider()),
            CurieProvider.NONE,
            MessageResolver.DEFAULTS_ONLY,
        ),
    )
}

private fun paths(fields: List<RestDocsField>) = fields.map { it.descriptor.path }

private fun field(fields: List<RestDocsField>, path: String) = fields.first { it.descriptor.path == path }

class HateoasExtractionTest :
    BehaviorSpec({
        given("EntityModel") {
            then("내용의 필드를 펼치고 _links 를 링크 객체(subsection)로 둔다") {
                val fields = responseFieldsOf<EntityModel<Book>>()

                paths(fields) shouldContainExactly listOf("id", "title", "publishedYear", "_links")
                field(fields, "_links").descriptor.isOptional shouldBe true
                field(fields, "_links").descriptor.description.toString() shouldStartWith "Links to related resources"
            }

            then("실제 HAL 직렬화 결과와 맞는다 (링크가 없어도)") {
                verifyResponseFields(EntityModel.of(BOOK, SELF), halMapper()) { responseFieldsOf<EntityModel<Book>>() }
                verifyResponseFields(EntityModel.of(BOOK), halMapper()) { responseFieldsOf<EntityModel<Book>>() }
            }

            then("다른 객체의 프로퍼티여도 그 아래에 펼친다") {
                val shelf = Shelf("new", EntityModel.of(BOOK, SELF))

                paths(responseFieldsOf<Shelf>()) shouldContainExactly listOf(
                    "name",
                    "featured",
                    "featured.id",
                    "featured.title",
                    "featured.publishedYear",
                    "featured._links",
                )
                verifyResponseFields(shelf, halMapper()) { responseFieldsOf<Shelf>() }
            }
        }

        given("RepresentationModel 을 상속한 클래스") {
            then("links 대신 _links 로 추출하고 실제 직렬화와 맞는다") {
                val model = BookModel(1, "Kotlin").add(SELF)

                paths(responseFieldsOf<BookModel>()) shouldContainExactly listOf("id", "title", "_links")
                verifyResponseFields(model, halMapper()) { responseFieldsOf<BookModel>() }
            }
        }

        given("CollectionModel") {
            then("원소 타입 이름으로 _embedded 관계를 만든다 (기본 규칙 <타입>List)") {
                val fields = responseFieldsOf<CollectionModel<EntityModel<Book>>>()

                paths(fields) shouldContainExactly listOf(
                    "_embedded",
                    "_embedded.bookList",
                    "_embedded.bookList[].id",
                    "_embedded.bookList[].title",
                    "_embedded.bookList[].publishedYear",
                    "_embedded.bookList[]._links",
                    "_links",
                )
                field(fields, "_embedded").descriptor.isOptional shouldBe true
                field(fields, "_embedded.bookList").descriptor.description.toString() shouldBe
                    "Resources in this collection"
            }

            then("실제 HAL 직렬화 결과와 맞는다 (비어 있으면 _embedded 가 빠져도 통과)") {
                val books = CollectionModel.of(listOf(EntityModel.of(BOOK, SELF)), SELF)

                verifyResponseFields(books, halMapper()) { responseFieldsOf<CollectionModel<EntityModel<Book>>>() }
                verifyResponseFields(CollectionModel.empty<EntityModel<Book>>(), halMapper()) {
                    responseFieldsOf<CollectionModel<EntityModel<Book>>>()
                }
            }

            then("원소가 EntityModel 이 아니어도, RepresentationModel 하위 클래스여도 맞는다") {
                val plain = CollectionModel.of(listOf(BOOK))
                val models = CollectionModel.of(listOf(BookModel(1, "Kotlin").add(SELF)))

                paths(responseFieldsOf<CollectionModel<Book>>()) shouldContainExactly listOf(
                    "_embedded",
                    "_embedded.bookList",
                    "_embedded.bookList[].id",
                    "_embedded.bookList[].title",
                    "_embedded.bookList[].publishedYear",
                    "_links",
                )
                verifyResponseFields(plain, halMapper()) { responseFieldsOf<CollectionModel<Book>>() }
                verifyResponseFields(models, halMapper()) { responseFieldsOf<CollectionModel<BookModel>>() }
            }

            then("@Relation(collectionRelation) 이 있으면 그 이름이다") {
                val magazines = CollectionModel.of(listOf(EntityModel.of(Magazine(1, 7))))

                paths(responseFieldsOf<CollectionModel<EntityModel<Magazine>>>()) shouldContainExactly listOf(
                    "_embedded",
                    "_embedded.magazines",
                    "_embedded.magazines[].id",
                    "_embedded.magazines[].issue",
                    "_embedded.magazines[]._links",
                    "_links",
                )
                verifyResponseFields(magazines, halMapper()) {
                    responseFieldsOf<CollectionModel<EntityModel<Magazine>>>()
                }
            }
        }

        given("PagedModel") {
            val metadata = PagedModel.PageMetadata(1, 0, 3)

            then("_embedded, _links, page 로 추출하고 실제 HAL 직렬화와 맞는다") {
                val page = PagedModel.of(listOf(EntityModel.of(BOOK, SELF)), metadata, SELF)

                paths(responseFieldsOf<PagedModel<EntityModel<Book>>>()) shouldContainExactly listOf(
                    "_embedded",
                    "_embedded.bookList",
                    "_embedded.bookList[].id",
                    "_embedded.bookList[].title",
                    "_embedded.bookList[].publishedYear",
                    "_embedded.bookList[]._links",
                    "_links",
                    "page",
                    "page.size",
                    "page.totalElements",
                    "page.totalPages",
                    "page.number",
                )
                verifyResponseFields(page, halMapper()) { responseFieldsOf<PagedModel<EntityModel<Book>>>() }
            }

            then("앱의 이름 규칙은 내용에만 적용되고 HAL 키와 page 키는 그대로다") {
                // HATEOAS 2.5 의 SNAKE_CASE 지원은 이 테스트의 Jackson(2.21)에 없는 클래스를 써서 설정으로 확인한다
                val snakeCase = { name: String -> name.replace(Regex("([A-Z])")) { "_" + it.value.lowercase() } }

                RestDocsConfiguration.withSettings({
                    extraction.propertyNameStrategy =
                        PropertyNameStrategy { snakeCase(it.name) }
                }) {
                    paths(responseFieldsOf<PagedModel<EntityModel<Book>>>()) shouldContainExactly listOf(
                        "_embedded",
                        "_embedded.bookList",
                        "_embedded.bookList[].id",
                        "_embedded.bookList[].title",
                        "_embedded.bookList[].published_year",
                        "_embedded.bookList[]._links",
                        "_links",
                        "page",
                        "page.size",
                        "page.totalElements",
                        "page.totalPages",
                        "page.number",
                    )
                }
            }
        }

        given("HAL 필드 설명") {
            then("language 가 ko 면 한국어다") {
                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    val fields = responseFieldsOf<PagedModel<EntityModel<Book>>>()

                    field(fields, "_embedded.bookList").descriptor.description.toString() shouldBe "이 컬렉션의 리소스"
                    field(fields, "page.totalElements").descriptor.description.toString() shouldBe "전체 항목 수"
                }
            }
        }
    })
