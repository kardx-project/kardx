package io.github.kardx.config

/**
 * 메시지·SSE 문서화의 AsyncAPI 메타데이터.
 * [RestDocsSettings.asyncApi] 로 읽고 `asyncApi { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     asyncApi { enabled = false }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class AsyncApiSettings internal constructor() {
    /**
     * 메시지·SSE 문서화 때 AsyncAPI 메타데이터(`*.asyncapi.json`)를 남길지 여부. 기본값 true.
     * 끄면 AsyncAPI 문서를 만들 재료가 없어진다. AsciiDoc 스니펫에는 영향이 없다. yml: `kardx.async-api.enabled`.
     */
    public var enabled: Boolean = true

    internal fun copy(): AsyncApiSettings = AsyncApiSettings().also {
        it.enabled = enabled
    }
}
