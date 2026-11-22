package io.github.kardx.lifecycle

/**
 * 테스트 프레임워크 확장을 만들 때만 쓰는 API 표시. 일반 테스트 코드에서는 쓰지 않는다.
 *
 * 이 라이브러리의 JUnit 확장(`AutoRestDocumentationExtension`)과 Kotest 확장(`kardx-kotest` 의
 * `RestDocsExtension`)이 함께 쓰는 문서화 수명주기([DocumentationLifecycle])와 문서 구조 모델([DocumentOutline])에 붙는다.
 * 다른 테스트 프레임워크용 확장을 직접 만들 때 `@OptIn(RestDocsExtensionApi::class)` 로 쓴다.
 * 공개 API 이지만 확장 제작자용이라 일반 API 보다 자주 바뀔 수 있다.
 */
@RequiresOptIn(
    message = "API for test framework extensions. Tests use AutoRestDocumentationExtension or RestDocsExtension.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
@MustBeDocumented
public annotation class RestDocsExtensionApi
