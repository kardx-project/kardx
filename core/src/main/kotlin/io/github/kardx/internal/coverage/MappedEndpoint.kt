package io.github.kardx.internal.coverage

/**
 * 앱에 매핑된 엔드포인트 하나 (HTTP 메서드 × 경로 패턴). 문서화 범위(coverage) 비교의 기준이다.
 *
 * @property method HTTP 메서드 (`GET`). 매핑에 메서드가 없으면 `*` (모든 메서드)
 * @property path 경로 패턴 (`/api/v1/users/{id}`)
 * @property handler 컨트롤러 메서드 (`com.example.UserController#getUser`)
 * @property deprecated 핸들러 메서드나 컨트롤러에 `@Deprecated` 가 있는지
 * @property apiVersion 핸들러에 선언한 API 버전 (Spring Framework 7). 없으면 null
 */
internal class MappedEndpoint(
    val method: String,
    val path: String,
    val handler: String,
    val deprecated: Boolean,
    val apiVersion: String?,
) {
    /** 인벤토리 파일의 항목. 키 순서를 고정한다. */
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "method" to method,
        "path" to path,
        "handler" to handler,
        "deprecated" to deprecated,
        "apiVersion" to apiVersion,
    )

    /** 같은 엔드포인트인지 가리고 정렬하는 키. 인벤토리 id 도 이 키로 만든다. */
    val key: String get() = listOf(path, method, apiVersion.orEmpty(), handler, deprecated).joinToString(" ")

    companion object {
        /** 메서드 조건이 없는 매핑의 메서드 */
        const val ANY_METHOD: String = "*"
    }
}
