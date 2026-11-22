package io.github.kardx.internal.diagnostics

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.introspection.FieldExtractor
import io.github.kardx.internal.jackson.deserializeJson

/** 진단용 JSON 읽기. 문서화하지 않은 부분을 REST Docs 경로로 펼치고, 실제 페이로드에서 경로의 값을 찾는다. */
internal object PayloadPaths {
    /** 경로에 값이 없을 때 [valueAt] 이 돌려주는 표시 (값이 `null` 인 것과 구별한다) */
    val ABSENT: Any = Any()

    private val SEGMENT = Regex("""\['([^']*)']|\[]|[^.\[]+""")

    /** JSON 이면 `Map`·`List`·값으로 읽은 결과, 읽을 수 없으면(JSON 이 아님, Jackson 없음) null. */
    fun parse(json: String): Any? = runCatching {
        deserializeJson<Any?>(RestDocsConfiguration.objectMapper(), json, Any::class.java)
    }.getOrNull()

    /**
     * 값이 있는 끝 경로. 객체는 `a.b`, 객체 원소가 있는 배열은 `a[].b`, 값 배열·빈 배열·빈 객체는 그 경로 하나다.
     * 같은 경로는 한 번만 둔다.
     */
    fun leaves(node: Any?): List<PayloadLeaf> = collect(node, "").filter { it.path.isNotEmpty() }
        .distinctBy { it.path }

    /** [path] (`a.b`, `['a.b'].c`) 의 값. 객체만 따라가며 배열(`[]`)을 만나면 멈춘다. 없으면 [ABSENT]. */
    fun valueAt(node: Any?, path: String): Any? {
        var current = node

        for (segment in segments(path)) {
            val map = current as? Map<*, *>

            if (map == null || !map.containsKey(segment)) {
                return ABSENT
            }

            current = map[segment]
        }

        return current
    }

    /** 경로 조각. `['a.b']` 는 따옴표 안의 이름, `[]` 는 그대로. */
    fun segments(path: String): List<String> = SEGMENT.findAll(path)
        .map { it.groupValues[1].ifEmpty { it.value } }
        .toList()

    /** REST Docs 의 타입 이름. */
    fun typeOf(value: Any?): String = when (value) {
        null -> "Null"
        is Map<*, *> -> "Object"
        is List<*> -> "Array"
        is String -> "String"
        is Number -> "Number"
        is Boolean -> "Boolean"
        else -> "Varies"
    }

    private fun collect(node: Any?, path: String): List<PayloadLeaf> = when {
        node is Map<*, *> && node.isNotEmpty() -> node.entries.flatMap { (key, value) ->
            collect(value, FieldExtractor.joinPath(path, key.toString()))
        }

        node is List<*> && node.any { it is Map<*, *> || it is List<*> } -> node.flatMap { collect(it, "$path[]") }

        else -> listOf(PayloadLeaf(path, typeOf(node)))
    }
}
