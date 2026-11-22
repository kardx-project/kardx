package io.github.kardx.extractor

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.support.verifyResponseFields
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.data.web.PagedModel

data class Article(val id: Long, val title: String)

/** Spring Data 의 `VIA_DTO` 모드처럼 `PageImpl` 을 `PagedModel` 로 바꿔 내보내는 mixin. */
@JsonSerialize(converter = PageToPagedModel::class)
abstract class PagedModelMixin

class PageToPagedModel : StdConverter<Page<*>, PagedModel<*>>() {
    override fun convert(value: Page<*>): PagedModel<*> = PagedModel(value)
}

private val ARTICLES = listOf(Article(1, "a"), Article(2, "b"))

private fun paths(fields: List<RestDocsField>) = fields.map { it.descriptor.path }

private fun description(field: RestDocsField) = field.descriptor.description.toString()

class SpringDataExtractionTest :
    BehaviorSpec({
        given("Spring Data Page 응답") {
            val page = PageImpl(ARTICLES, PageRequest.of(0, 2, Sort.by("id")), 10)

            then("배열이 아니라 Jackson 이 내보내는 객체 모양으로 추출한다") {
                paths(responseFieldsOf<Page<Article>>()) shouldContainExactly listOf(
                    "content",
                    "content[].id",
                    "content[].title",
                    "pageable",
                    "pageable.pageNumber",
                    "pageable.pageSize",
                    "pageable.sort",
                    "pageable.sort.empty",
                    "pageable.sort.sorted",
                    "pageable.sort.unsorted",
                    "pageable.offset",
                    "pageable.paged",
                    "pageable.unpaged",
                    "totalElements",
                    "totalPages",
                    "last",
                    "first",
                    "size",
                    "number",
                    "sort",
                    "sort.empty",
                    "sort.sorted",
                    "sort.unsorted",
                    "numberOfElements",
                    "empty",
                )
            }

            then("실제 PageImpl 직렬화 결과와 맞는다 (Page, PageImpl 로 선언해도 같다)") {
                verifyResponseFields(page) { responseFieldsOf<Page<Article>>() }
                verifyResponseFields(page) { responseFieldsOf<PageImpl<Article>>() }
            }

            then("매퍼에 PageImpl mixin 이 있으면(VIA_DTO) content 와 page 로 추출한다") {
                val mapper = ObjectMapper().addMixIn(PageImpl::class.java, PagedModelMixin::class.java)

                verifyResponseFields(page, mapper) { responseFieldsOf<Page<Article>>() }
            }
        }

        given("Spring Data 필드·파라미터 설명") {
            then("기본은 영어다") {
                val fields = responseFieldsOf<Page<Article>>().associate { it.descriptor.path to description(it) }

                fields["totalElements"] shouldBe "Total number of items"
                fields["pageable.pageNumber"] shouldBe "Page number (zero-based)"
                fields["sort.sorted"] shouldBe "Whether the result is sorted"
                parametersOf<Pageable>().map { it.descriptor.description.toString() } shouldContainExactly listOf(
                    "Page number (zero-based)",
                    "Page size",
                    "Sort order in the format `property,asc|desc`. Can be repeated (e.g. `sort=name,desc`)",
                )
            }

            then("language 가 ko 면 한국어다 (같은 타입을 앞에서 영어로 추출했어도)") {
                RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    val fields = responseFieldsOf<Page<Article>>().associate { it.descriptor.path to description(it) }

                    fields["totalElements"] shouldBe "전체 항목 수"
                    fields["content"] shouldBe "이 페이지의 항목"
                    fields["pageable.offset"] shouldBe "건너뛴 항목 수 (pageNumber × pageSize)"
                    parametersOf<Pageable>().map { it.descriptor.description.toString() } shouldContainExactly listOf(
                        "페이지 번호 (0부터)",
                        "페이지 크기",
                        "정렬. `속성,asc|desc` 형식이고 여러 번 보낼 수 있다 (예: `sort=name,desc`)",
                    )
                }
            }
        }

        given("Spring Data Slice 와 PagedModel 응답") {
            then("Slice 는 전체 개수 없이 객체로 추출하고 실제 직렬화와 맞는다") {
                val slice = SliceImpl(ARTICLES, PageRequest.of(0, 2), true)

                paths(responseFieldsOf<Slice<Article>>()) shouldContainExactly listOf(
                    "content",
                    "content[].id",
                    "content[].title",
                    "pageable",
                    "pageable.pageNumber",
                    "pageable.pageSize",
                    "pageable.sort",
                    "pageable.sort.empty",
                    "pageable.sort.sorted",
                    "pageable.sort.unsorted",
                    "pageable.offset",
                    "pageable.paged",
                    "pageable.unpaged",
                    "first",
                    "last",
                    "size",
                    "number",
                    "sort",
                    "sort.empty",
                    "sort.sorted",
                    "sort.unsorted",
                    "numberOfElements",
                    "empty",
                )

                verifyResponseFields(slice) { responseFieldsOf<Slice<Article>>() }
            }

            then("PagedModel 은 content 와 page 로 추출한다") {
                val model = PagedModel(PageImpl(ARTICLES, PageRequest.of(0, 2), 10))

                verifyResponseFields(model) { responseFieldsOf<PagedModel<Article>>() }
            }
        }

        given("Pageable·Sort 파라미터") {
            then("Pageable 은 page, size, sort 쿼리 파라미터이고 모두 optional 이다") {
                val parameters = parametersOf<Pageable>()

                parameters.map { it.descriptor.name } shouldContainExactly listOf("page", "size", "sort")
                parameters.all { it.descriptor.isOptional } shouldBe true
            }

            then("Sort 는 sort 파라미터 하나다") {
                parametersOf<Sort>().map { it.descriptor.name } shouldContainExactly listOf("sort")
            }

            then("PageRequest 처럼 Pageable 구현체도 같다") {
                parametersOf<PageRequest>().map { it.descriptor.name } shouldContainExactly
                    listOf("page", "size", "sort")
            }
        }
    })
