package io.github.kardx.dsl

import io.github.kardx.ExperimentalRestDocsApi
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.restdocs.request.RequestPartDescriptor

/**
 * multipart 요청 파트 DSL. `"file" partMeans "업로드할 이미지"`
 *
 * 수식어는 이 객체를 바꾸지 않고 복사한 새 객체를 돌려준다. 그래서 `val` 상수로 빼서 여러 테스트가 같이 써도 된다.
 */
public class RestDocsPart(public val descriptor: RequestPartDescriptor) {
    /** true 이면 [optional] 과 같다. false 는 아무것도 바꾸지 않는다 (이미 optional 이면 그대로 둔다). */
    public infix fun isOptional(value: Boolean): RestDocsPart = if (value) optional() else this

    /** 없어도 되는 항목으로 표시한다. 테스트 요청에 없어도 실패하지 않는다. AsciiDoc 표의 Required 열이 `false` 가 된다. */
    public fun optional(): RestDocsPart = modified { it.optional() }

    /** 디스크립터까지 복사한 파트에 [change] 를 적용한다. 이 파트는 그대로다. */
    internal inline fun modified(change: (RequestPartDescriptor) -> Unit): RestDocsPart {
        val copy = RequestDocumentation.partWithName(descriptor.name)
            .description(descriptor.description)

        copy.attributes.putAll(descriptor.attributes)

        if (descriptor.isOptional) copy.optional()

        if (descriptor.isIgnored) copy.ignored()

        change(copy)

        return RestDocsPart(copy)
    }
}

/**
 * 파트 DSL 의 시작점. [io.github.kardx.dsl.DocumentationBuilder.requestParts] 에 넘긴다.
 * AsciiDoc `request-parts` 표가 되고, Gradle 플러그인이 OpenAPI `multipart/form-data` 요청 본문의 속성으로 넣는다
 * (파일 파트는 `string`/`binary`, 본문 필드를 적은 파트는 그 스키마). 파트 본문 필드는 `requestPartFields` 로 따로 적는다.
 */
public infix fun String.partMeans(description: String): RestDocsPart = RestDocsPart(
    RequestDocumentation.partWithName(this)
        .description(description),
)

/** 설명을 핸들러의 `@param` 에서 가져오는 파트. `"thumbnail" partMeans FromParamTag isOptional true` */
@Suppress("UnusedParameter") // FromParamTag 는 오버로드를 고르는 표식이다
@ExperimentalRestDocsApi
public infix fun String.partMeans(description: FromParamTag): RestDocsPart = undescribedPart(this)

/** 설명 없이 이름만 적은 파트. 설명은 핸들러의 `@param` 에서 채운다 ([FromParamTag]). */
internal fun undescribedPart(name: String): RestDocsPart = RestDocsPart(RequestDocumentation.partWithName(name))
