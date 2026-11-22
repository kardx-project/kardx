package io.github.kardx.internal.jackson

/**
 * Jackson 매퍼의 직렬화 설정. Jackson 2 는 `getSerializationConfig()`, Jackson 3 은 `serializationConfig()` 다.
 * 라이브러리는 Jackson 에 컴파일 의존하지 않으므로 리플렉션으로 부른다. 알 수 없으면 null.
 */
internal fun serializationConfigOf(mapper: Any): Any? = SERIALIZATION_CONFIG_GETTERS.firstNotNullOfOrNull { name ->
    runCatching { mapper.javaClass.getMethod(name).invoke(mapper) }.getOrNull()
}

/** 매퍼의 직렬화 설정에 [target] 용으로 등록된 mixin 클래스. 없거나 알 수 없으면 null. */
internal fun mixInClassFor(mapper: Any, target: Class<*>): Class<*>? {
    val config = serializationConfigOf(mapper) ?: return null

    return runCatching {
        config.javaClass.getMethod(FIND_MIX_IN, Class::class.java)
            .invoke(config, target) as? Class<*>
    }.getOrNull()
}

private val SERIALIZATION_CONFIG_GETTERS = listOf("serializationConfig", "getSerializationConfig")
private const val FIND_MIX_IN = "findMixInClassFor"
