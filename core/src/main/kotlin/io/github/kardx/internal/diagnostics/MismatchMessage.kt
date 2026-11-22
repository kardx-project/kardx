package io.github.kardx.internal.diagnostics

/**
 * [io.github.kardx.dsl.DocumentationMismatchException] 의 메시지. 블록마다 빈 줄로 나눈다.
 *
 * ```
 * Documented response fields do not match the actual response.
 *
 * Not documented (present in the response):
 *   - data.memo (String)
 *
 * Documented but not in the response:
 *   - data.items[].price
 *   Remove them, or mark them `isOptional true` if they can be absent.
 *
 * Add to the documentation (replace "..." with a description):
 *   "data.memo" type STRING means "..."
 *
 * Hints:
 *   - `data.items` is an empty array here, ...
 *
 * The original Spring REST Docs message is the cause of this exception.
 * ```
 *
 * 필드 타입 검증 실패([renderType])는 목록 대신 문서화한 타입과 실제 타입을 보여 준다.
 *
 * ```
 * Documented response fields do not match the actual response.
 *
 * Type mismatch:
 *   - price: documented Number, actual String
 *
 * Change the documentation (replace "..." with a description):
 *   "price" type STRING means "..."
 *
 * Hints:
 *   - `price` is documented as a number but the payload has a string. ...
 *
 * The original Spring REST Docs message is the cause of this exception.
 * ```
 */
internal object MismatchMessage {
    private const val INDENT = "  "
    private const val FIELDS_LIMIT = 50
    private const val CAUSE = "The original Spring REST Docs message is the cause of this exception."

    /** REST Docs 의 null 타입 표기 */
    private const val NULL = "Null"

    /** 이름으로 문서화하는 항목의 DSL 함수 (`"page" queryMeans "..."`). 헤더는 실제로 없는 경우만 있어 넣지 않는다. */
    private val NAME_DSL = mapOf(
        "Query parameters" to "queryMeans",
        "Path parameters" to "pathMeans",
        "Form parameters" to "formMeans",
        "Cookies" to "cookieMeans",
        "Request parts" to "partMeans",
        "Events" to "eventMeans",
    )

    fun render(subject: String, target: String, mismatch: SnippetMismatch, hints: List<String>): String = listOfNotNull(
        title(subject, target),
        undocumented(target, mismatch),
        missing(target, mismatch.missing),
        suggestions(mismatch),
        block("Hints:", hints.map { "- $it" }),
        CAUSE,
    ).joinToString("\n\n")

    /** 필드 타입 검증 실패의 메시지. */
    fun renderType(subject: String, target: String, mismatch: FieldTypeMismatch, hints: List<String>): String {
        val path = mismatch.path
        val documented = mismatch.documented
        val actual = mismatch.actual
        val problem = if (documented == null) {
            block(
                "Not in the $target and documented without a type, so the type cannot be determined:",
                listOf("- $path"),
            )
        } else {
            block("Type mismatch:", listOf("- $path: documented $documented, actual $actual"))
        }

        val line = when {
            documented == null -> "\"$path\" type STRING means \"...\" isOptional true"
            actual == NULL -> "\"$path\" type ${documented.uppercase()} means \"...\" isOptional true"
            else -> "\"$path\" type ${actual.orEmpty().uppercase()} means \"...\""
        }

        return listOfNotNull(
            title(subject, target),
            problem,
            block("Change the documentation (replace \"...\" with a description):", listOf(line)),
            block("Hints:", hints.map { "- $it" }),
            CAUSE,
        ).joinToString("\n\n")
    }

    private fun title(subject: String, target: String) = "Documented $subject do not match the actual $target."

    private fun undocumented(target: String, mismatch: SnippetMismatch): String? {
        val title = "Not documented (present in the $target):"

        mismatch.undocumentedText?.let { return block(title, it.lines()) }

        return block(
            title,
            mismatch.undocumented.take(FIELDS_LIMIT)
                .map { leaf -> if (leaf.type.isEmpty()) "- ${leaf.path}" else "- ${leaf.path} (${leaf.type})" } +
                more(mismatch.undocumented.size),
        )
    }

    private fun missing(target: String, missing: List<String>): String? = block(
        "Documented but not in the $target:",
        missing.take(FIELDS_LIMIT)
            .map {
                "- $it"
            } + more(missing.size) + "Remove them, or mark them `isOptional true` if they can be absent.",
    ).takeIf { missing.isNotEmpty() }

    private fun suggestions(mismatch: SnippetMismatch): String? {
        val lines = if (mismatch.isFields) {
            mismatch.undocumented.map { "\"${it.path}\" type ${it.type.uppercase()} means \"...\"" }
        } else {
            NAME_DSL[mismatch.subject]?.let { dsl -> mismatch.undocumented.map { "\"${it.path}\" $dsl \"...\"" } }
                .orEmpty()
        }

        return block("Add to the documentation (replace \"...\" with a description):", lines.take(FIELDS_LIMIT))
    }

    private fun more(size: Int): List<String> = listOfNotNull(
        "- ... ${size - FIELDS_LIMIT} more".takeIf {
            size >
                FIELDS_LIMIT
        },
    )

    /** 제목과 들여 쓴 줄. 줄이 없으면 null. */
    private fun block(title: String, lines: List<String>): String? = lines.takeIf { it.isNotEmpty() }
        ?.let { listOf(title) + it.map { line -> INDENT + line } }
        ?.joinToString("\n")
}
