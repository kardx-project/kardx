package io.github.kardx.internal.documentation

import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.output.OpenRun
import io.github.kardx.internal.output.OpenRuns
import io.github.kardx.internal.output.SnippetPaths
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.operation.Operation
import org.springframework.restdocs.snippet.Snippet
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 테스트 확장이 연 실행 안에서, 그 실행이 만들지 않은 REST Docs 문맥으로 문서화했는지 본다. 스니펫은 만들지 않는다.
 *
 * 흔한 경우는 Spring Boot `@AutoConfigureRestDocs` 로 주입받은 `MockMvc`·`WebTestClient` 다. Boot 는 스니펫 디렉터리를 테스트
 * 클래스·메서드 이름으로만 정하므로(`ManualRestDocumentation`), `@Nested`·`@ParameterizedTest`·이름이 같은 오버로드 메서드의
 * 스니펫이 문서가 읽는 자리와 다른 곳에 생겨 문서에서 그 섹션이 오류 없이 빠진다.
 *
 * - 스니펫이 문서가 읽는 자리와 다른 곳에 생기면 [IllegalStateException] 으로 실패한다
 * - 같은 자리면(평범한 테스트 메서드) 지금은 문서에 들어가므로 JVM 에서 한 번 경고만 한다
 * - 확장이 연 실행이 없으면(REST Docs 의 `RestDocumentationExtension` 만 쓰는 테스트) 검사하지 않는다
 *
 * 이 라이브러리의 스니펫보다 먼저 둔다. 그래야 `api-summary`·OpenAPI 조각이 다른 곳에 생기기 전에 실패한다
 * (REST Docs 기본 스니펫인 `curl-request` 등은 REST Docs 가 먼저 쓴다).
 */
internal object DocumentationContextCheck : Snippet {
    private val LOGGER: System.Logger = System.getLogger("io.github.kardx.junit")
    private val WARNED = AtomicBoolean()

    override fun document(operation: Operation) {
        val context = SnippetPaths.documentationContext(operation) ?: return

        if (OpenRuns.isOwn(context)) return

        val run = OpenRuns.current() ?: return
        val actual = File(context.outputDirectory, SnippetPaths.documentIdentifier(operation, context))
        val expected = run.snippetDirectory

        check(actual.canonicalFile == expected.canonicalFile) {
            RestDocsErrorCode.FOREIGN_DOCUMENTATION_CONTEXT.message(failure(context, actual, run))
        }

        if (WARNED.compareAndSet(false, true)) {
            LOGGER.log(
                System.Logger.Level.WARNING,
                RestDocsErrorCode.FOREIGN_DOCUMENTATION_CONTEXT.message(warning(run)),
            )
        }
    }

    private fun failure(context: RestDocumentationContext, actual: File, run: OpenRun): String =
        "${run.documentClass.simpleName}.${context.testMethodName} was documented with a REST Docs configuration " +
            "that kardx did not create (typically the MockMvc or WebTestClient injected by Spring Boot " +
            "with @AutoConfigureRestDocs). Its snippets go to '$actual', but the document reads " +
            "'${run.snippetDirectory}', so this section would be missing from the generated document. $FIX"

    private fun warning(run: OpenRun): String =
        "${run.documentClass.simpleName} is documented with a REST Docs configuration that kardx did not " +
            "create (typically the MockMvc or WebTestClient injected by Spring Boot with @AutoConfigureRestDocs). " +
            "It works for plain test methods, but sections of @Nested classes, @ParameterizedTest runs and " +
            "overloaded test methods would go missing from the document. $FIX"

    private const val FIX = "Take the client as a test method parameter (fun getUser(mockMvc: MockMvc)) with " +
        "AutoRestDocumentationExtension, or build it with restDocsMockMvc(context) / restDocsWebTestClient(context)."
}
