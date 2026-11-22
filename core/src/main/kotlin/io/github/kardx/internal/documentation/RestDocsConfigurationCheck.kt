package io.github.kardx.internal.documentation

import io.github.kardx.diagnostics.RestDocsErrorCode

/** REST Docs 가 설정 없는 클라이언트로 문서화할 때 던지는 예외 메시지의 앞부분 (MockMvc·WebTestClient 같다) */
private const val CONFIGURATION_NOT_FOUND = "REST Docs configuration not found"

/**
 * [block] 에서 REST Docs 의 "REST Docs configuration not found" 예외가 나면 어떻게 고치는지 알려 주는 메시지로 바꾼다.
 * Spring Boot 가 주입한 `MockMvc`(`@AutoConfigureMockMvc`, `@WebMvcTest`)를 그대로 쓰면 이렇게 된다.
 *
 * @param client 메시지에 쓸 클라이언트 이름 (`MockMvc`, `WebTestClient`)
 * @param fix 고치는 방법 한 문장
 */
internal fun <T> withRestDocsConfiguration(client: String, fix: String, block: () -> T): T = try {
    block()
} catch (e: IllegalStateException) {
    if (e.message?.startsWith(CONFIGURATION_NOT_FOUND) != true) throw e

    throw IllegalStateException(
        RestDocsErrorCode.REST_DOCS_NOT_CONFIGURED.message(
            "this $client has no REST Docs configuration, so andDocument cannot write snippets. A $client " +
                "injected by Spring Boot (@AutoConfigureMockMvc, @WebMvcTest, @AutoConfigureWebTestClient) is not " +
                "set up for REST Docs. $fix",
        ),
        e,
    )
}
