package io.github.kardx.internal.diagnostics

/** 페이로드 안의 값 하나. [type] 은 REST Docs 표기(`String`, `Number`, `Object` …)다. */
internal class PayloadLeaf(val path: String, val type: String)

/**
 * REST Docs 검증 실패 메시지를 읽은 결과. 필드는 경로, 파라미터·헤더·쿠키·파트·이벤트는 이름이다.
 *
 * @property subject 메시지의 주어 (`Fields`, `Query parameters`, `Headers` …)
 * @property undocumented 실제로 있지만 문서화하지 않은 항목. 필드는 실제 JSON 타입이 붙는다
 * @property undocumentedText 문서화하지 않은 본문을 JSON 으로 읽지 못했을 때의 원문 (XML 등)
 * @property missing 문서화했지만 실제로 없는 항목
 */
internal class SnippetMismatch(
    val subject: String,
    val undocumented: List<PayloadLeaf>,
    val undocumentedText: String?,
    val missing: List<String>,
) {
    val isFields: Boolean get() = subject == FIELDS

    companion object {
        const val FIELDS = "Fields"

        /**
         * 이름 목록 형식: `Query parameters with the following names were not documented: [a, b]`.
         * 목록은 `. ` 이나 줄 끝 앞의 `]` 에서 끝난다 (경로 안의 `[]` 에서 끊지 않는다).
         */
        private val NAMES =
            Regex(
                """([A-Z][A-Za-z ]*?) with the following (?:names|paths) were not (documented|found)[^:]*: """ +
                    """\[(.*?)](?=\.\s|\R|$)""",
            )

        private val PAYLOAD =
            Regex("""The following parts of the payload were not documented:\R([\s\S]*?)(?=\RFields with|\z)""")

        /** 알아볼 수 있는 검증 실패 메시지면 읽은 결과, 아니면(다른 종류의 실패) null. */
        fun parse(message: String): SnippetMismatch? {
            val lists = NAMES.findAll(message)
                .toList()
            val payload = PAYLOAD.find(message)
                ?.groupValues
                ?.get(1)
                ?.trim()

            if (lists.isEmpty() && payload == null) {
                return null
            }

            val subject = lists.firstOrNull()
                ?.destructured
                ?.component1() ?: FIELDS

            fun names(kind: String) = lists.map { it.destructured }
                .filter { (_, verb, _) -> verb == kind }
                .flatMap { (_, _, names) -> split(names) }

            val parsed = payload?.let(PayloadPaths::parse)

            return SnippetMismatch(
                subject = subject,
                undocumented = parsed?.let(PayloadPaths::leaves)
                    ?: names("documented").map { PayloadLeaf(it, "") },
                undocumentedText = payload?.takeIf { parsed == null },
                missing = names("found"),
            )
        }

        private fun split(list: String): List<String> = list.split(", ")
            .filter { it.isNotEmpty() }
    }
}
