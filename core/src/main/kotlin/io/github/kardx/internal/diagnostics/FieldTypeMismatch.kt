package io.github.kardx.internal.diagnostics

/**
 * REST Docs 의 필드 타입 검증 실패를 읽은 결과.
 *
 * - `FieldTypesDoNotMatchException`: `The documented type of the field 'price' is Number but the actual type is String`
 * - `FieldTypeRequiredException`: `Cannot determine the type of the field 'memo' as it is not present in the payload. ...`
 *
 * 타입은 REST Docs 표기(`String`, `Number`, `Null`, `Varies` …)다.
 *
 * @property path 필드 경로
 * @property documented 문서화한 타입. 타입 없이 문서화했으면(`FieldTypeRequiredException`) null
 * @property actual 실제 페이로드의 타입. 페이로드에 없으면 null
 */
internal class FieldTypeMismatch(val path: String, val documented: String?, val actual: String?) {
    companion object {
        /** 이름으로만 알아본다. REST Docs 가 패키지 밖에 공개하지 않는 클래스다. */
        private const val TYPES_DO_NOT_MATCH = "org.springframework.restdocs.payload.FieldTypesDoNotMatchException"
        private const val TYPE_REQUIRED = "org.springframework.restdocs.payload.FieldTypeRequiredException"

        private val DO_NOT_MATCH =
            Regex("""The documented type of the field '(.+)' is (\S+) but the actual type is (\S+)""")
        private val REQUIRED = Regex("""Cannot determine the type of the field '(.+)' as it is not present in the""")

        /** REST Docs 의 필드 타입 검증 실패면 읽은 결과, 아니면(다른 예외, XML 페이로드의 타입 누락 등) null. */
        fun of(error: Throwable): FieldTypeMismatch? {
            val message = error.message.orEmpty()

            return when (error.javaClass.name) {
                TYPES_DO_NOT_MATCH -> DO_NOT_MATCH.find(message)
                    ?.destructured
                    ?.let { (path, documented, actual) -> FieldTypeMismatch(path, documented, actual) }

                TYPE_REQUIRED -> REQUIRED.find(message)
                    ?.groupValues
                    ?.get(1)
                    ?.let { FieldTypeMismatch(it, null, null) }

                else -> null
            }
        }
    }
}
