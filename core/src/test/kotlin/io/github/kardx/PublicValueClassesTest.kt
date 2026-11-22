package io.github.kardx

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.asyncapi.AsyncApiApiKeyLocation
import io.github.kardx.asyncapi.AsyncApiInfo
import io.github.kardx.asyncapi.AsyncApiSecurityScheme
import io.github.kardx.asyncapi.AsyncApiServer
import io.github.kardx.config.ResponseWrapper
import io.github.kardx.config.SnippetFile
import io.github.kardx.config.SnippetInclude
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * data class 에서 일반 class 로 바꾼 공개 값 클래스가 값 의미(equals/hashCode/toString)를 지키는지.
 * [variants] 는 기준값과 프로퍼티 하나씩만 다른 값들이다.
 */
private fun <T : Any> BehaviorSpec.valueContract(
    name: String,
    create: () -> T,
    variants: List<T>,
    expectedToString: String,
) {
    given(name) {
        val value = create()
        val same = create()

        then("같은 값이면 equals 와 hashCode 가 같다") {
            value shouldBe same
            value.hashCode() shouldBe same.hashCode()
            value shouldBe value
        }

        then("프로퍼티 하나만 달라도 다르다") {
            variants.forEach { it shouldNotBe value }
        }

        then("null 이나 다른 타입과는 다르다") {
            val nothing: Any? = null

            (value == nothing) shouldBe false
            (value == Any()) shouldBe false
        }

        then("toString 은 클래스 이름과 프로퍼티를 보여 준다") {
            value.toString() shouldBe expectedToString
        }

        then("집합의 키로 쓸 수 있다") {
            setOf(value, same).size shouldBe 1
        }
    }
}

class PublicValueClassesTest :
    BehaviorSpec({
        val server = AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws", "local")

        valueContract(
            name = "AsyncApiServer",
            create = { AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws", "local") },
            variants = listOf(
                AsyncApiServer("other", "localhost:8080", "stomp", "/ws", "local"),
                AsyncApiServer("stomp", "example.com", "stomp", "/ws", "local"),
                AsyncApiServer("stomp", "localhost:8080", "ws", "/ws", "local"),
                AsyncApiServer("stomp", "localhost:8080", "stomp", null, "local"),
                AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws", null),
            ),
            expectedToString = "AsyncApiServer(name=stomp, host=localhost:8080, protocol=stomp, pathname=/ws, " +
                "description=local)",
        )

        val bearer = AsyncApiSecurityScheme.bearer("bearerAuth")

        valueContract(
            name = "AsyncApiInfo",
            create = { AsyncApiInfo("T", "1", "d", listOf(server), listOf(bearer)) },
            variants = listOf(
                AsyncApiInfo("X", "1", "d", listOf(server), listOf(bearer)),
                AsyncApiInfo("T", "2", "d", listOf(server), listOf(bearer)),
                AsyncApiInfo("T", "1", null, listOf(server), listOf(bearer)),
                AsyncApiInfo("T", "1", "d", securitySchemes = listOf(bearer)),
                AsyncApiInfo("T", "1", "d", listOf(server)),
            ),
            expectedToString = "AsyncApiInfo(title=T, version=1, description=d, servers=[$server], " +
                "securitySchemes=[$bearer])",
        )

        valueContract(
            name = "AsyncApiSecurityScheme",
            create = { AsyncApiSecurityScheme.apiKey("key", AsyncApiApiKeyLocation.HEADER, "X-API-KEY") },
            variants = listOf(
                AsyncApiSecurityScheme.apiKey("other", AsyncApiApiKeyLocation.HEADER, "X-API-KEY"),
                AsyncApiSecurityScheme.apiKey("key", AsyncApiApiKeyLocation.QUERY, "X-API-KEY"),
                AsyncApiSecurityScheme.apiKey("key", AsyncApiApiKeyLocation.HEADER, "X-KEY"),
                AsyncApiSecurityScheme.bearer("key"),
                AsyncApiSecurityScheme.basic("key"),
            ),
            expectedToString = "AsyncApiSecurityScheme(name=key, type=API_KEY, location=HEADER, " +
                "parameterName=X-API-KEY)",
        )

        valueContract(
            name = "ResponseWrapper",
            create = { ResponseWrapper(String::class, "result") },
            variants = listOf(
                ResponseWrapper(Int::class, "result"),
                ResponseWrapper(String::class),
            ),
            expectedToString = "ResponseWrapper(wrapperClass=${String::class}, dataPath=result)",
        )

        valueContract(
            name = "DocumentGenerator.Config",
            create = { DocumentGenerator.Config() },
            variants = listOf(
                DocumentGenerator.Config(doctype = "article"),
                DocumentGenerator.Config(icons = ""),
                DocumentGenerator.Config(sourceHighlighter = "rouge"),
                DocumentGenerator.Config(toc = "right"),
                DocumentGenerator.Config(tocLevels = null),
                DocumentGenerator.Config(isSectLinks = false),
            ),
            expectedToString = "Config(doctype=book, icons=font, sourceHighlighter=highlightjs, toc=left, " +
                "tocLevels=3, isSectLinks=true)",
        )

        valueContract(
            name = "SnippetInclude",
            create = { SnippetInclude(SnippetFile.CURL_INFO, "curl-request.adoc") },
            variants = listOf(
                SnippetInclude(null, "curl-request.adoc"),
                SnippetInclude(SnippetFile.CURL_INFO, "other.adoc"),
            ),
            expectedToString = "SnippetInclude(kind=CURL_INFO, fileName=curl-request.adoc)",
        )
    })
