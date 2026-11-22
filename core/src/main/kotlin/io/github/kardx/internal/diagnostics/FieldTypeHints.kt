package io.github.kardx.internal.diagnostics

/**
 * 필드 타입 검증 실패([FieldTypeMismatch])의 흔한 원인을 힌트로 만든다 (`docs/troubleshooting.md` 의 표와 같은 내용).
 *
 * 1. 실제 값이 null: nullable 프로퍼티 또는 `isOptional true` (optional 필드는 null 을 허용한다)
 * 2. 배열 원소마다 타입이 다름(`Varies`): `VARIES` 로 문서화하거나 값의 타입을 맞춘다. 일부 원소에서 null·빠진 값은
 *    REST Docs 가 "없는 필드" 로 보고하므로 여기에 오지 않는다
 * 3. 숫자로 문서화했는데 문자열: `@JsonFormat(shape = STRING)`·`ToStringSerializer`·`WRITE_NUMBERS_AS_STRINGS`
 * 4. 문자열로 문서화했는데 숫자·배열: 날짜·시간을 타임스탬프로 쓰는 매퍼 설정 (`WRITE_DATES_AS_TIMESTAMPS`), 숫자 `@JsonValue` Enum
 * 5. 타입 없이 문서화했는데 페이로드에 없음: `type` 으로 타입을 정한다
 */
internal object FieldTypeHints {
    /** REST Docs 의 타입 표기 */
    private const val NULL = "Null"
    private const val VARIES = "Varies"
    private const val NUMBER = "Number"
    private const val STRING = "String"
    private const val ARRAY = "Array"

    fun of(mismatch: FieldTypeMismatch): List<String> {
        val path = mismatch.path
        val documented = mismatch.documented
        val actual = mismatch.actual

        return listOfNotNull(
            required(path).takeIf { documented == null },
            nullValue(path).takeIf { actual == NULL },
            varies(path).takeIf { actual == VARIES },
            numberAsString(path).takeIf { documented == NUMBER && actual == STRING },
            dateAsTimestamp(path).takeIf { documented == STRING && (actual == NUMBER || actual == ARRAY) },
        )
    }

    private fun required(path: String): String =
        "`$path` is not in the payload and has no documented type, so Spring REST Docs cannot determine its type. " +
            "Give it a type with `type` (replace STRING above with the field's type)."

    private fun nullValue(path: String): String =
        "`$path` is null in this payload. If it can be null, make the property nullable (extraction marks nullable " +
            "properties optional) or mark the field `isOptional true` (for an extracted list: " +
            "`.optionalAt(\"$path\")`); optional fields accept null. Otherwise fill it in the test data."

    private fun varies(path: String): String {
        val array = path.substringBeforeLast("[]", "")
            .takeIf { it.isNotEmpty() }
            ?.let { "the elements of `$it[]`" } ?: "the matched values"

        return "`$path` has different types across $array (e.g. a number in one and a string in another). " +
            "Document it as VARIES if that is intended; otherwise make the values the same type in the application."
    }

    private fun numberAsString(path: String): String =
        "`$path` is documented as a number but the payload has a string. Common causes: " +
            "@JsonFormat(shape = JsonFormat.Shape.STRING) or @JsonSerialize(using = ToStringSerializer::class) " +
            "on the property of a hand-documented field, or a mapper that writes numbers as strings " +
            "(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS). If the string is intended, document it as STRING."

    private fun dateAsTimestamp(path: String): String =
        "`$path` is documented as a string but the payload has a number or an array. Dates and times are written " +
            "that way (1762300800000, [2026,11,5]) when the mapper enables WRITE_DATES_AS_TIMESTAMPS " +
            "(SerializationFeature in Jackson 2, DateTimeFeature in Jackson 3; Spring Boot's mapper disables it); " +
            "disable it to get ISO strings. An enum whose @JsonValue is a number is also written as a number. " +
            "Otherwise document the actual type."
}
