package io.github.kardx.internal.sourcedocs

import io.github.kardx.internal.jackson.DefaultObjectMapper
import io.github.kardx.internal.jackson.deserializeJson

/**
 * Gradle 플러그인(`apiDocsSourceDocs`)이 소스의 KDoc·Javadoc 에서 모은 설명 색인 (`source-docs.json`, version 1).
 *
 * ```json
 * { "version": 1,
 *   "types": { "com.example.UserResponse": { "description": "…", "properties": { "id": "사용자 ID" },
 *                                             "enumConstants": { "ACTIVE": "활성" } } },
 *   "methods": { "com.example.UserController#getUser": { "summary": "…", "description": "…",
 *                                                        "params": { "id": "사용자 ID" } },
 *                "com.example.UserController#search/2": { "summary": "…", "description": "…" } } }
 * ```
 *
 * 타입 키는 JVM 이진 이름(`Outer$Inner`), 프로퍼티 키는 소스의 선언 이름이다 (Jackson 이름 규칙은 적용하지 않은 이름).
 * 메서드의 `params` 키는 소스의 인자 이름이다 (`@param` 태그). 메서드 KDoc 을 끈 색인에는 `params` 만 있을 수 있다.
 * 비어 있거나 공백뿐인 설명은 없는 것으로 본다.
 */
internal class SourceDocsIndex private constructor(
    private val types: Map<String, TypeDocs>,
    private val methods: Map<String, MethodDocs>,
) {
    /** 이진 이름 [className] 타입의 설명. 없으면 null. */
    fun type(className: String): TypeDocs? = types[className]

    /** `fqcn#name` 또는 `fqcn#name/<인자 수>` 메서드의 설명. 없으면 null. */
    fun method(key: String): MethodDocs? = methods[key]

    companion object {
        private const val SUPPORTED_VERSION = 1
        private const val DESCRIPTION = "description"

        /**
         * 색인 파일 내용을 읽는다.
         *
         * @throws IllegalArgumentException 지원하지 않는 version 이거나 JSON 객체가 아닐 때
         */
        fun parse(json: String): SourceDocsIndex {
            val root = deserializeJson<Any?>(DefaultObjectMapper.instance, json, Map::class.java) as? Map<*, *>
                ?: throw IllegalArgumentException("source docs index is not a JSON object")
            val version = (root["version"] as? Number)?.toInt()

            require(version == SUPPORTED_VERSION) { "unsupported source docs index version '${root["version"]}'" }

            val types = root.objectEntries("types")
                .mapValues { (_, type) -> TypeDocs(type.texts("properties"), type.texts("enumConstants")) }
            val methods = root.objectEntries("methods")
                .mapValues { (_, method) ->
                    MethodDocs(method.text("summary"), method.text(DESCRIPTION), method.texts("params"))
                }

            return SourceDocsIndex(types, methods)
        }

        /** [key] 객체의 항목 중 값이 객체인 것. */
        private fun Map<*, *>.objectEntries(key: String): Map<String, Map<*, *>> = (this[key] as? Map<*, *>).orEmpty()
            .entries
            .mapNotNull { (name, value) -> (value as? Map<*, *>)?.let { name.toString() to it } }
            .toMap()

        /** [key] 객체의 항목 중 값이 비어 있지 않은 문자열인 것. */
        private fun Map<*, *>.texts(key: String): Map<String, String> = (this[key] as? Map<*, *>).orEmpty()
            .entries
            .mapNotNull { (name, value) -> nonBlank(value)?.let { name.toString() to it } }
            .toMap()

        private fun Map<*, *>.text(key: String): String? = nonBlank(this[key])

        private fun nonBlank(value: Any?): String? = (value as? String)?.takeIf { it.isNotBlank() }
    }
}

/**
 * 타입 하나의 설명. 타입 자체의 설명(`description`)은 아직 쓰는 곳이 없어 읽지 않는다.
 *
 * @property properties 소스 선언 이름 → 프로퍼티 설명
 * @property enumConstants Enum 상수 이름 → 상수 설명
 */
internal class TypeDocs(val properties: Map<String, String>, val enumConstants: Map<String, String>)

/**
 * 핸들러 메서드 하나의 설명.
 *
 * @property summary 첫 문장
 * @property description 전체 설명
 * @property params 소스의 인자 이름 → `@param` 설명
 */
internal class MethodDocs(val summary: String?, val description: String?, val params: Map<String, String> = emptyMap())
