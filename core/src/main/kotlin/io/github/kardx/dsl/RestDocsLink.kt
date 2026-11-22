package io.github.kardx.dsl

import org.springframework.restdocs.hypermedia.HypermediaDocumentation
import org.springframework.restdocs.hypermedia.LinkDescriptor

/**
 * 하이퍼미디어 링크 DSL. `"self" linkMeans "자기 자신"` (HAL / Atom 형식 자동 감지)
 *
 * 수식어는 이 객체를 바꾸지 않고 복사한 새 객체를 돌려준다. 그래서 `val` 상수로 빼서 여러 테스트가 같이 써도 된다.
 */
public class RestDocsLink(public val descriptor: LinkDescriptor) {
    /** true 이면 [optional] 과 같다. false 는 아무것도 바꾸지 않는다 (이미 optional 이면 그대로 둔다). */
    public infix fun isOptional(value: Boolean): RestDocsLink = if (value) optional() else this

    /** 없어도 되는 항목으로 표시한다. 테스트 응답에 없어도 실패하지 않는다. AsciiDoc 표의 Required 열이 `false` 가 된다. */
    public fun optional(): RestDocsLink {
        val copy = HypermediaDocumentation.linkWithRel(descriptor.rel)
            .description(descriptor.description)

        copy.attributes.putAll(descriptor.attributes)
        copy.optional()

        if (descriptor.isIgnored) copy.ignored()

        return RestDocsLink(copy)
    }
}

/** 링크 DSL 의 시작점. [io.github.kardx.dsl.DocumentationBuilder.links] 에 넘기면 AsciiDoc `links` 표가 된다. */
public infix fun String.linkMeans(description: String): RestDocsLink = RestDocsLink(
    HypermediaDocumentation.linkWithRel(this)
        .description(description),
)
