package io.github.kardx.internal.openapi

import com.epages.restdocs.apispec.ResourceDocumentation
import com.epages.restdocs.apispec.ResourceSnippetParametersBuilder
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private fun mapperOf(snippet: Any): Any? = snippet.javaClass.getDeclaredField("objectMapper")
    .apply { isAccessible = true }
    .get(snippet)

class SharedResourceMapperTest :
    BehaviorSpec({
        given("epages 리소스 스니펫 여러 개") {
            val first = ResourceDocumentation.resource(ResourceSnippetParametersBuilder().build())
            val second = ResourceDocumentation.resource(ResourceSnippetParametersBuilder().build())

            then("epages 는 스니펫마다 매퍼를 새로 만든다") {
                (mapperOf(first) === mapperOf(second)) shouldBe false
            }

            `when`("공유하면") {
                SharedResourceMapper.share(first)
                SharedResourceMapper.share(second)

                then("뒤의 스니펫도 처음 공유한 매퍼를 쓴다") {
                    val third = SharedResourceMapper.share(
                        ResourceDocumentation.resource(ResourceSnippetParametersBuilder().build()),
                    )

                    mapperOf(third) shouldBeSameInstanceAs mapperOf(second)
                }
            }
        }

        given("매퍼 필드가 없는 객체") {
            then("건드리지 않고 그대로 돌려준다") {
                val plain = Any()

                SharedResourceMapper.share(plain) shouldBeSameInstanceAs plain
            }
        }
    })
