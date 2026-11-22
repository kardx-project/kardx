package io.github.kardx.config

/**
 * 산출물(스니펫, AsciiDoc 문서, WireMock 매핑) 위치와 종류.
 * [RestDocsSettings.output] 로 읽고 `output { }` 블록으로 바꾼다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     output { wireMockStubs = true }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class OutputSettings internal constructor() {
    /**
     * `andDocument` 가 요청·응답마다 WireMock 매핑 JSON 을 남길지 여부. 기본값 false.
     * 위치는 `<스니펫 디렉터리>/wiremock/mappings/<class-name>/<method-name>.json` 이다. WireMock 의존성은 필요 없다.
     * Gradle 플러그인의 `kardx { wireMock { enabled = true } }` 가 켠다. yml: `kardx.wire-mock-stubs`.
     */
    public var wireMockStubs: Boolean = false

    /**
     * 스니펫 디렉터리. `null` 이면 Spring REST Docs 와 같은 규칙으로 정한다.
     * 작업 디렉터리에 `pom.xml` 이 있으면 `target/generated-snippets`, 아니면 `build/generated-snippets`.
     * 첫 테스트 직전에 읽으므로 yml 이나 `@BeforeAll` 에서 정한다. yml: `kardx.snippets.directory`.
     */
    public var snippetsDirectory: String? = null

    /**
     * AsciiDoc 문서를 쓰는 디렉터리. `null` 이면 스니펫 디렉터리와 같은 빌드 디렉터리의 `generated-asciidoc`
     * (`build/generated-asciidoc` 또는 `target/generated-asciidoc`). yml: `kardx.asciidoc-directory`.
     */
    public var asciidocDirectory: String? = null

    internal fun copy(): OutputSettings = OutputSettings().also {
        it.wireMockStubs = wireMockStubs
        it.snippetsDirectory = snippetsDirectory
        it.asciidocDirectory = asciidocDirectory
    }
}
