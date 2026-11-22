package io.github.kardx.internal.websocket

import io.github.kardx.internal.documentation.CachedTemplateEngine
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.config.OperationPreprocessorsConfigurer
import org.springframework.restdocs.config.RestDocumentationConfigurer
import org.springframework.restdocs.config.SnippetConfigurer
import org.springframework.restdocs.generate.RestDocumentationGenerator
import org.springframework.restdocs.snippet.Snippet

/**
 * 문서화 한 번에 쓰는 REST Docs 설정 맵(스니펫 설정, 템플릿 엔진, 파일 경로 리졸버, 컨텍스트)을 만든다. 템플릿 엔진은
 * 컴파일한 템플릿을 이어 쓰는 [CachedTemplateEngine] 이다.
 * MockMvc·WebTestClient 설정기와 같은 기본값을 spring-restdocs-core 의 [RestDocumentationConfigurer] 로 직접 채우므로
 * 테스트 클라이언트 모듈(spring-restdocs-mockmvc·webtestclient) 없이 동작한다. 순번(`stepCount`)은 MockMvc 요청 한 번과
 * 똑같이 [RestDocumentationContextProvider.beforeOperation] 으로 하나 올린다.
 * 메시지는 기본 스니펫(curl, http-request 등)이 의미가 없으므로 비운다. HTTP 교환(RestTestClient)은 [httpSnippets] 로 남긴다.
 */
internal fun documentationConfigurationMap(
    provider: RestDocumentationContextProvider,
    httpSnippets: Boolean = false,
): MutableMap<String, Any> = MessageConfigurer().configuration(provider.beforeOperation(), httpSnippets)

/** 기본값만 쓰는 설정기. REST Docs 3.0 과 4.0 이 같은 구조(스니펫·전처리·템플릿 엔진·리졸버)를 쓴다. */
private class MessageConfigurer :
    RestDocumentationConfigurer<MessageSnippetConfigurer, MessagePreprocessorsConfigurer, MessageConfigurer>() {
    private val snippets = MessageSnippetConfigurer(this)
    private val preprocessors = MessagePreprocessorsConfigurer(this)

    init {
        templateEngine(CachedTemplateEngine)
    }

    override fun snippets(): MessageSnippetConfigurer = snippets

    override fun operationPreprocessors(): MessagePreprocessorsConfigurer = preprocessors

    fun configuration(context: RestDocumentationContext, httpSnippets: Boolean): MutableMap<String, Any> {
        val configuration = HashMap<String, Any>()

        configuration[RestDocumentationContext::class.java.name] = context
        apply(configuration, context)

        if (!httpSnippets) {
            configuration[RestDocumentationGenerator.ATTRIBUTE_NAME_DEFAULT_SNIPPETS] = emptyList<Snippet>()
        }

        return configuration
    }
}

private class MessageSnippetConfigurer(parent: MessageConfigurer) :
    SnippetConfigurer<MessageConfigurer, MessageSnippetConfigurer>(parent)

private class MessagePreprocessorsConfigurer(parent: MessageConfigurer) :
    OperationPreprocessorsConfigurer<MessageConfigurer, MessagePreprocessorsConfigurer>(parent)
