package io.github.kardx.internal.asyncapi

import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.i18n.ViewerText
import io.github.kardx.internal.jackson.serializeJson

/**
 * AsyncAPI 뷰어 HTML. 두 탭으로 이루어진다.
 *
 * - 문서: AsyncAPI 공식 React 뷰어 (`@asyncapi/react-component` 3.2.3)
 * - Try it out: 스펙의 서버·목적지·예시로 STOMP(`@stomp/stompjs` 7.5.0) / WebSocket / SSE 를 직접 주고받는 화면.
 *   서버 주소는 페이지를 연 앱 주소가 기본값이다
 *
 * 스펙을 페이지 안에 넣으므로 HTML 파일 하나로 동작한다. 라이브러리는 CDN 에서 버전을 고정해 읽는다.
 * 화면 문구는 만들 때의 언어([io.github.kardx.config.RestDocsSettings.language])로 넣는다 ([ViewerText]).
 * 소스는 리소스 `viewer.html`·`viewer.css`·`viewer.js` 로 나뉘어 있고, 만들 때 CSS·JS 를 HTML 안에 넣는다.
 * 리소스의 주석은 만든 페이지에 넣지 않는다 ([WebComments]).
 */
internal object AsyncApiViewer {
    private const val INDENT = "    "

    /** 주석을 지운 CSS·JS 를 넣은 페이지. `__TITLE__`, `__SPEC_JSON__`, `__LANG__`, `__VIEWER_TEXTS__` 만 남는다 */
    private val page: String by lazy {
        WebComments.stripHtml(resource("viewer.html"))
            .replace("__VIEWER_CSS__", indent(WebComments.stripCss(resource("viewer.css"))))
            .replace("__VIEWER_JS__", indent(WebComments.stripJs(resource("viewer.js"))))
    }

    /** 페이지의 자리 표시자. 한 번에 바꿔서, 넣은 값(제목·스펙·문구) 안의 같은 글자는 다시 바꾸지 않는다 */
    private val PLACEHOLDER = Regex("__(TITLE|SPEC_JSON|LANG|VIEWER_TEXTS)__")

    fun html(
        title: String,
        document: Map<String, Any?>,
        language: DocsLanguage = RestDocsConfiguration.current.language,
    ): String {
        val values = mapOf(
            "TITLE" to escapeHtml(title),
            "SPEC_JSON" to scriptJson(document),
            "LANG" to language.name.lowercase(),
            "VIEWER_TEXTS" to scriptJson(ViewerText.all(language)),
        )

        return PLACEHOLDER.replace(page) { values.getValue(it.groupValues[1]) }
    }

    /**
     * `<script>` 안에 넣을 JSON (스펙, 화면 문구). `<` 를 모두 이스케이프해야 `<!--` + `<script` 조합이 HTML 파서를 script 안에
     * 가두지 못한다. JSON 문자열 안에서 유효하다. U+2028/2029 는 오래된 JS 엔진에서 문자열을 끊으므로 이스케이프한다.
     */
    private fun scriptJson(value: Map<String, Any?>): String =
        serializeJson(RestDocsConfiguration.objectMapper(), value)
            .replace("<", "\\u003c")
            .replace(" ", "\\u2028")
            .replace(" ", "\\u2029")

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /** `<style>`·`<script>` 안에 맞춰 줄마다 들여쓴다. 빈 줄은 그대로 둔다. 끝의 줄바꿈은 템플릿이 붙인다. */
    private fun indent(source: String): String = source.trimEnd('\n')
        .lines()
        .joinToString("\n") { if (it.isEmpty()) it else INDENT + it }

    private fun resource(name: String): String =
        requireNotNull(AsyncApiViewer::class.java.getResource(name)) { "$name resource is missing" }
            .readText()
}
