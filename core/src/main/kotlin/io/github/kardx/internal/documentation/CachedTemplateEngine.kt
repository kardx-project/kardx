package io.github.kardx.internal.documentation

import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.config.OperationPreprocessorsConfigurer
import org.springframework.restdocs.config.RestDocumentationConfigurer
import org.springframework.restdocs.config.SnippetConfigurer
import org.springframework.restdocs.templates.Template
import org.springframework.restdocs.templates.TemplateEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 컴파일한 스니펫 템플릿을 JVM 안에서 이어 쓰는 템플릿 엔진. 라이브러리가 만드는 REST Docs 설정(`restDocsMockMvc`,
 * WebTestClient·RestTestClient 의 `restDocs(provider)`, 메시지 문서화)이 쓴다.
 *
 * REST Docs 는 요청마다 템플릿 엔진을 새로 만들어, 스니펫마다 클래스패스에서 템플릿을 찾고 Mustache 로 다시 컴파일한다
 * (요청당 약 0.3 ms). 템플릿은 클래스패스의 고정된 자원이므로 이름별로 한 번만 컴파일한다. 컴파일은 REST Docs 가 기본으로
 * 만드는 엔진(AsciiDoc 형식, UTF-8, 표 칸 람다 포함)에 맡기므로 결과는 같다. 컴파일한 Mustache 템플릿은 여러 스레드가
 * 같이 렌더링해도 안전하다 (변수 조회 캐시가 `ConcurrentHashMap`).
 */
internal object CachedTemplateEngine : TemplateEngine {
    private val delegate: TemplateEngine by lazy { DefaultsConfigurer().templateEngine() }
    private val templates = ConcurrentHashMap<String, Template>()

    override fun compileTemplate(name: String): Template =
        templates.computeIfAbsent(name) { delegate.compileTemplate(it) }
}

/**
 * 아무것도 바꾸지 않은 REST Docs 설정기. REST Docs 3.0 과 4.0 의 기본 템플릿 엔진을 그 버전의 규칙 그대로 얻는다
 * (Mustache 컴파일러 패키지가 버전마다 달라 직접 만들 수 없다).
 */
private class DefaultsConfigurer :
    RestDocumentationConfigurer<DefaultsSnippetConfigurer, DefaultsPreprocessorsConfigurer, DefaultsConfigurer>() {
    private val snippets = DefaultsSnippetConfigurer(this)
    private val preprocessors = DefaultsPreprocessorsConfigurer(this)

    override fun snippets(): DefaultsSnippetConfigurer = snippets

    override fun operationPreprocessors(): DefaultsPreprocessorsConfigurer = preprocessors

    fun templateEngine(): TemplateEngine {
        val configuration = HashMap<String, Any>()

        apply(configuration, NoContext)

        return configuration[TemplateEngine::class.java.name] as TemplateEngine
    }
}

private class DefaultsSnippetConfigurer(parent: DefaultsConfigurer) :
    SnippetConfigurer<DefaultsConfigurer, DefaultsSnippetConfigurer>(parent)

private class DefaultsPreprocessorsConfigurer(parent: DefaultsConfigurer) :
    OperationPreprocessorsConfigurer<DefaultsConfigurer, DefaultsPreprocessorsConfigurer>(parent)

/** 기본값을 채우는 데는 문맥을 쓰지 않는다. REST Docs 의 `apply` 가 인자로 요구해서 넘긴다. */
private object NoContext : RestDocumentationContext {
    override fun getTestClass(): Class<*> = NoContext::class.java

    override fun getTestMethodName(): String = ""

    override fun getOutputDirectory(): File = File("")

    override fun getStepCount(): Int = 0
}
