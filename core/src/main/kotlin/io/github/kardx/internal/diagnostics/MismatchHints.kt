package io.github.kardx.internal.diagnostics

/**
 * 검증 실패의 흔한 원인 중 실제 페이로드로 확인되는 것만 힌트로 만든다 (`docs/troubleshooting.md` 의 표와 같은 내용).
 * 앞선 규칙이 설명한 "실제로 없는 항목" 은 뒤 규칙에서 다시 보지 않는다.
 *
 * 1. 빈 배열: `items[].price` 가 없는데 `items` 가 빈 배열이다. `optionalUnder("items[]")` 를 제안한다
 * 2. 이름 전략: 대소문자·`_`·`-` 만 다른 이름이 양쪽에 있다 (`userName` ↔ `user_name`)
 * 3. 응답 래퍼: 문서는 최상위인데 실제는 `data` 아래에 있거나, 그 반대다
 * 4. 부모는 있는데 빠진 필드: null 이라 직렬화에서 빠졌을 수 있다 (`@JsonInclude(NON_NULL)`)
 *
 * 힌트의 코드는 그대로 붙여 넣으면 등록까지 되는 단축형(`responseFields<T>()` 등)으로 보여 준다.
 * 블록 안에서 `responseFieldsOf<T>()` 는 목록만 돌려주고 아무것도 등록하지 않기 때문이다.
 */
internal object MismatchHints {
    private const val ARRAY = "[]"
    private const val RESPONSE_FIELDS = "response fields"

    private class Hint(val text: String, val explains: Collection<String>)

    /** @param subject 실패한 스니펫의 항목 이름 (`response fields`, `request fields` …). 힌트의 등록 코드를 고른다 */
    fun of(mismatch: SnippetMismatch, payload: Any?, subject: String): List<String> {
        val undocumented = mismatch.undocumented.map { it.path }
        val rules: List<(List<String>) -> Hint?> = if (mismatch.isFields) {
            listOf(
                { missing -> emptyArrays(missing, payload, subject) },
                { missing -> naming(missing, undocumented, fields = true) },
                { missing -> wrappedPayload(missing, undocumented, subject) },
                { missing -> unwrappedPayload(missing, undocumented, subject) },
                { missing -> absentInParent(missing, payload) },
            )
        } else {
            listOf({ missing -> naming(missing, undocumented, fields = false) })
        }

        var remaining = mismatch.missing

        return rules.mapNotNull { rule ->
            rule(remaining)?.also { hint -> remaining = remaining - hint.explains.toSet() }
                ?.text
        }
    }

    private fun emptyArrays(missing: List<String>, payload: Any?, subject: String): Hint? {
        val empty = missing.filter { ARRAY in it }
            .groupBy { it.substringBefore(ARRAY) }
            .filterKeys { (PayloadPaths.valueAt(payload, it) as? List<*>)?.isEmpty() == true }
            .ifEmpty { return null }
        val arrays = empty.keys.joinToString { if (it.isEmpty()) "the payload itself" else "`$it`" }
        val elements = empty.keys.joinToString { "\"$it[]\"" }

        return Hint(
            "$arrays is an empty array here, so fields inside its elements cannot be checked. Put elements in the " +
                "test data, or mark the element fields optional: ${optionalElements(subject, elements)} " +
                "(for every array: kardx.array-element-fields-optional: true).",
            empty.values.flatten(),
        )
    }

    /**
     * [subject] 스니펫에 타입 `T` 의 필드를 등록하면서 [elements] 아래를 optional 로 두는 코드 (`optionalUnder`).
     * 목록을 고쳐야 하므로 단축형이 아니라 목록 버전에 추출 함수(`responseFieldsOf<T>()` 등)를 넘긴다.
     */
    private fun optionalElements(subject: String, elements: String): String {
        fun extracted(function: String) = "$function<T>().optionalUnder($elements)"

        val request = extracted("requestFieldsOf")
        val response = extracted("responseFieldsOf")

        return when (subject) {
            "request fields" -> "requestFields($request)"
            "request part fields" -> "requestPartFields(\"<part>\", $request)"
            "message payload fields" -> "fields($request) in send, fields($response) in subscribe"
            "event data fields" -> "events(\"<event>\" eventMeans \"...\" withFields $response)"
            else -> "responseFields($response)"
        }
    }

    private fun naming(missing: List<String>, undocumented: List<String>, fields: Boolean): Hint? {
        val pairs = missing.mapNotNull { documented ->
            undocumented.firstOrNull { it != documented && normalize(it) == normalize(documented) }
                ?.let { documented to it }
        }.ifEmpty { return null }
        val list = pairs.joinToString { (documented, actual) -> "`$documented` (documented) vs `$actual` (actual)" }
        val cause = if (fields) {
            "The application serializes with a different naming strategy than extraction used. Extraction follows " +
                "@JsonNaming and the naming strategy of RestDocsSettings.extraction.objectMapper: build MockMvc with " +
                "restDocsMockMvc (it uses the application's mapper) or set objectMapper to the application's mapper."
        } else {
            "Document the name the request actually uses."
        }

        return Hint("Names differ only in case or separators: $list. $cause", pairs.map { it.first })
    }

    /** 문서는 최상위 `id` 인데 실제는 `data.id` 다: 래퍼로 감싼 응답을 래퍼 없이 문서화했다. */
    private fun wrappedPayload(missing: List<String>, undocumented: List<String>, subject: String): Hint? {
        val prefix = missing.map { field -> undocumented.mapNotNull { prefixOf(it, field) }.toSet() }
            .reduceOrNull { common, prefixes -> common intersect prefixes }
            ?.minOrNull() ?: return null
        val fix = if (subject == RESPONSE_FIELDS) {
            "If every response is wrapped, register the wrapper (kardx.response-wrapper.class / " +
                "data-path) and use wrappedResponseFields<T>(); otherwise use ${registration(subject, prefix)}."
        } else {
            "Document them with ${registration(subject, prefix)}."
        }

        return Hint(
            "The payload has these fields under `$prefix` (e.g. `$prefix.${missing.first()}`), but they are " +
                "documented at the top level. $fix",
            missing,
        )
    }

    /** 문서는 `data.id` 인데 실제는 최상위 `id` 다: 감싸지 않은 응답을 래퍼와 함께 문서화했다. */
    private fun unwrappedPayload(missing: List<String>, undocumented: List<String>, subject: String): Hint? {
        val moved = missing.filter { '.' in it && it.substringAfter('.') in undocumented }
            .ifEmpty { return null }
        val prefix = moved.first()
            .substringBefore('.')
        val explained = missing.filter { it == prefix || it.startsWith("$prefix.") || ('.' !in it && ARRAY !in it) }
        val fix = if (subject == RESPONSE_FIELDS) {
            "Document this body with responseFields<T>() instead of wrappedResponseFields<T>()."
        } else {
            "Document these fields without the `$prefix.` prefix, e.g. with ${registration(subject)}."
        }

        return Hint(
            "Fields documented under `$prefix` (e.g. `${moved.first()}`) are at the top level of this payload: " +
                "it is not wrapped. $fix",
            explained,
        )
    }

    /** 부모 객체는 있는데 키가 없다. 값이 null 이라 직렬화에서 빠졌을 수 있다. */
    private fun absentInParent(missing: List<String>, payload: Any?): Hint? {
        val absent = missing.filter { field ->
            val segments = PayloadPaths.segments(field)
            val parent = PayloadPaths.valueAt(payload, field.substringBeforeLast('.', ""))

            ARRAY !in segments && "['" !in field && parent is Map<*, *> && !parent.containsKey(segments.last())
        }.ifEmpty { return null }
        val list = absent.joinToString { "`$it`" }

        return Hint(
            "Absent although the parent object is present: $list. If the value was null and the mapper omits nulls " +
                "(@JsonInclude(NON_NULL), spring.jackson.default-property-inclusion: non_null), mark it " +
                "`isOptional true` or make the property nullable; otherwise fill it in the test data.",
            absent,
        )
    }

    /**
     * [subject] 스니펫에 타입 `T` 의 필드를 등록하는 코드. 붙여 넣으면 바로 등록되는 단축형이다.
     * SSE 이벤트만 `withFields` 에 넘길 목록이 필요하므로 `responseFieldsOf` 를 쓴다.
     *
     * @param path 추출한 필드 앞에 붙일 경로. 비어 있으면 인자를 생략한다
     */
    private fun registration(subject: String, path: String = ""): String {
        val pathArgument = if (path.isEmpty()) emptyList() else listOf("\"$path\"")

        fun call(name: String, vararg leading: String) = (leading.toList() + pathArgument).joinToString(
            prefix = "$name<T>(",
            postfix = ")",
        )

        return when (subject) {
            "request fields" -> call("requestFields")
            "request part fields" -> call("requestPartFields", "\"<part>\"")
            "message payload fields" -> call("fields")
            "event data fields" -> "events(\"<event>\" eventMeans \"...\" withFields ${call("responseFieldsOf")})"
            else -> call("responseFields")
        }
    }

    /** [path] 가 `<prefix>.<field>` 이면 prefix. */
    private fun prefixOf(path: String, field: String): String? = path.takeIf { it.endsWith(".$field") }
        ?.removeSuffix(".$field")

    private fun normalize(name: String): String = name.lowercase()
        .filterNot { it == '_' || it == '-' }
}
