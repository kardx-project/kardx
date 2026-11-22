package io.github.kardx.internal.output

import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.RestDocumentationContextPlaceholderResolver
import org.springframework.restdocs.snippet.RestDocumentationContextPlaceholderResolverFactory
import org.springframework.util.PropertyPlaceholderHelper
import java.io.File

/** 테스트 클래스·메서드의 스니펫 경로. Spring REST Docs 와 같은 kebab-case 규칙을 쓴다. */
internal object SnippetPaths {
    /** 스니펫 디렉터리 식별자. REST Docs 가 `{class-name}/{method-name}` 을 kebab-case 로 치환한다. */
    const val DOCUMENT_IDENTIFIER: String = "{class-name}/{method-name}"

    /**
     * 스니펫 출력 디렉터리 기준의 스니펫 디렉터리 경로. `user-controller-test/get-user`
     *
     * Spring REST Docs 의 `{class-name}/{method-name}` 치환 규칙을 그대로 쓰므로 경로가 항상 일치한다.
     * [documentClass] 와 [methodName] 은 [SnippetNames] 가 정한 값이다.
     */
    fun relativeDirectory(documentClass: Class<*>, methodName: String): String {
        val names = restDocsNames(documentClass, methodName)

        return "${names.className}/${names.methodName}"
    }

    /** REST Docs 출력 디렉터리 아래 [documentClass] / [methodName] 의 스니펫 디렉터리. */
    fun snippetDirectory(outputDirectory: File, documentClass: Class<*>, methodName: String): File =
        File(outputDirectory, relativeDirectory(documentClass, methodName))

    /** 이 operation 의 REST Docs 문서화 문맥. 문서화 중이 아니면 null. */
    fun documentationContext(operation: Operation): RestDocumentationContext? =
        operation.attributes[RestDocumentationContext::class.java.name] as? RestDocumentationContext

    /** `{class-name}/{method-name}` 을 바꾼 문서 식별자 (`user-api-docs-test/get-user`). REST Docs·epages 와 같은 규칙이다. */
    fun documentIdentifier(operation: Operation, context: RestDocumentationContext): String =
        PropertyPlaceholderHelper("{", "}")
            .replacePlaceholders(operation.name, RestDocumentationContextPlaceholderResolverFactory().create(context))

    /**
     * camelCase 를 Spring REST Docs 와 같은 규칙으로 kebab-case 로 바꾼다.
     * `getUserInfo` → `get-user-info`, `UserControllerTest` → `user-controller-test`
     */
    fun camelCaseToDash(name: String): String =
        if (name.isEmpty()) "" else restDocsNames(Any::class.java, name).methodName

    private data class RestDocsNames(val className: String, val methodName: String)

    private fun restDocsNames(testClass: Class<*>, methodName: String): RestDocsNames {
        val documentation = ManualRestDocumentation(System.getProperty("java.io.tmpdir"))
        documentation.beforeTest(testClass, methodName)

        try {
            val resolver = RestDocumentationContextPlaceholderResolver(documentation.beforeOperation())

            return RestDocsNames(
                className = resolver.resolvePlaceholder("class-name") ?: testClass.simpleName,
                methodName = resolver.resolvePlaceholder("method-name") ?: methodName,
            )
        } finally {
            documentation.afterTest()
        }
    }
}
