package io.github.kardx.internal.http

import org.springframework.http.HttpHeaders

/**
 * Spring 6 / 7 모두에서 동작하는 [HttpHeaders] 접근.
 * Spring 7 의 HttpHeaders 는 Map 이 아니고 `get` 의 파라미터 타입도 달라서, 컴파일한 호출이 바이너리 호환되지 않는다.
 */
internal object HttpHeadersCompat {
    private val NAMES = listOf("headerNames", "keySet")
        .firstNotNullOfOrNull { name ->
            runCatching { HttpHeaders::class.java.getMethod(name) }
                .getOrNull()
        } ?: error("HttpHeaders.headerNames()/keySet() not found. Unsupported Spring Framework version.")

    private val GETTER = HttpHeaders::class.java.methods.firstOrNull {
        it.name == "get" && it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(String::class.java)
    } ?: error("HttpHeaders.get(String) not found. Unsupported Spring Framework version.")

    /** 헤더 이름 (요청·응답에 있는 대소문자 그대로). Spring 7 은 `headerNames()`, Spring 6 은 `keySet()`. */
    @Suppress("UNCHECKED_CAST")
    fun names(headers: HttpHeaders): Set<String> = (NAMES.invoke(headers) as? Set<String>).orEmpty()

    /** 헤더 값 목록. Spring 6 은 `get(Object)`, Spring 7 은 `get(String)`. */
    @Suppress("UNCHECKED_CAST")
    fun values(headers: HttpHeaders, name: String): List<String> =
        (GETTER.invoke(headers, name) as? List<String>).orEmpty()

    /** [replace] 로 값을 바꾼 복사본. [replace] 가 null 을 돌려주면 그 값은 뺀다. */
    fun copy(headers: HttpHeaders, replace: (name: String, value: String) -> String? = { _, v -> v }): HttpHeaders {
        val copied = HttpHeaders()
        val entries = names(headers)
            .flatMap { name ->
                values(headers, name)
                    .map { value -> name to value }
            }

        entries.forEach { (name, value) -> replace(name, value)?.let { copied.add(name, it) } }

        return copied
    }
}
