package io.github.kardx

/**
 * 아직 다듬는 중인 API 표시. 다음 릴리스에서 이름·동작이 바뀌거나 빠질 수 있고, 1.0 의 호환 보장 범위에 들지 않는다.
 *
 * 쓰는 곳에 `@OptIn(ExperimentalRestDocsApi::class)` 를 붙이거나, 모듈 전체에 켠다
 * (`kotlin { compilerOptions { optIn.add("io.github.kardx.ExperimentalRestDocsApi") } }`).
 * 붙이지 않으면 컴파일 경고가 난다.
 *
 * 지금 붙은 곳: KDoc·Javadoc 설명(`descriptions.sourceDocs`, [io.github.kardx.config.DescriptionResolver.SOURCE_DOCS],
 * `FromParamTag`, 설명 없이 이름만 적는 파라미터 선언), 클라이언트 어댑터 SPI(`io.github.kardx.client`),
 * 메시징 테스트 도구(`StompTestClient`, `WebSocketHandlerTester`).
 */
@RequiresOptIn(
    message = "Experimental kardx API. It may change or be removed in a future release.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
@MustBeDocumented
public annotation class ExperimentalRestDocsApi
