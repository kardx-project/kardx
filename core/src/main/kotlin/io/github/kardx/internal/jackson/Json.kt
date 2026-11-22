package io.github.kardx.internal.jackson

import io.github.kardx.diagnostics.RestDocsErrorCode
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type

/** Jackson 2/3 호환 직렬화. `writeValueAsString(Object)` 를 리플렉션으로 호출한다. */
internal fun serializeJson(mapper: Any, value: Any?): String = invokeMapper(mapper, "serialize") {
    mapper.javaClass.getMethod("writeValueAsString", Any::class.java)
        .invoke(mapper, value) as String
}

/**
 * Jackson 2/3 호환 역직렬화. `getTypeFactory().constructType(Type)` 으로 만든 JavaType 으로
 * `readValue(String, JavaType)` 를 리플렉션 호출한다.
 */
internal fun <T> deserializeJson(mapper: Any, content: String, type: Type): T = invokeMapper(mapper, "deserialize") {
    val typeFactory = mapper.javaClass.getMethod("getTypeFactory")
        .invoke(mapper)
    val javaType = typeFactory.javaClass.getMethod("constructType", Type::class.java)
        .invoke(typeFactory, type)
    val readValue = mapper.javaClass.methods.firstOrNull { method ->
        method.name == "readValue" && method.parameterCount == 2 &&
            method.parameterTypes[0] == String::class.java && method.parameterTypes[1].isInstance(javaType)
    } ?: throw NoSuchMethodException("${mapper.javaClass.name}.readValue(String, JavaType)")

    @Suppress("UNCHECKED_CAST")
    readValue.invoke(mapper, content, javaType) as T
}

/** [mapper] 로 JSON 문자열을 들여쓰기해 돌려준다. JSON 이 아니거나 [mapper] 가 Jackson 매퍼가 아니면 null. */
internal fun prettyJsonOrNull(json: String, mapper: Any): String? = runCatching {
    val tree = mapper.javaClass.getMethod("readTree", String::class.java)
        .invoke(mapper, json)
    val writer = mapper.javaClass.getMethod("writerWithDefaultPrettyPrinter")
        .invoke(mapper)

    writer.javaClass.getMethod("writeValueAsString", Any::class.java)
        .invoke(writer, tree) as String
}.getOrNull()

/**
 * [json] 이 JSON 문서 하나일 때만 [mapper] 로 들여쓰기해 돌려준다. [prettyJsonOrNull] 과 달리 문서 뒤에 다른 토큰이 있으면
 * (`{"a":1} junk`, `{"a":1} {"b":2}`) null 이다. Jackson 2 의 `readTree` 는 뒤의 토큰을 무시하므로
 * `DeserializationFeature.FAIL_ON_TRAILING_TOKENS` 를 켠 reader 로 읽는다 (Jackson 2/3 모두 같은 이름이다).
 */
internal fun prettySingleJsonOrNull(json: String, mapper: Any): String? {
    val tree = runCatching {
        val reader = strictReader(mapper)

        reader.javaClass.getMethod("readTree", String::class.java)
            .invoke(reader, json)
    }.getOrNull() ?: return null

    return prettyTree(tree, mapper)
}

/** `mapper.readerFor(Object).with(FAIL_ON_TRAILING_TOKENS)`. 기능 enum 은 매퍼의 Jackson 버전 패키지에서 찾는다. */
private fun strictReader(mapper: Any): Any {
    val objectMapper = generateSequence<Class<*>>(mapper.javaClass) { it.superclass }
        .first { it.simpleName == "ObjectMapper" }
    val featureName = "${objectMapper.packageName}.DeserializationFeature"
    val featureType = Class.forName(featureName, false, objectMapper.classLoader)
    val feature = featureType.enumConstants.first { (it as Enum<*>).name == "FAIL_ON_TRAILING_TOKENS" }
    val reader = objectMapper.getMethod("readerFor", Class::class.java)
        .invoke(mapper, Any::class.java)

    return reader.javaClass.getMethod("with", featureType)
        .invoke(reader, feature)
}

private fun prettyTree(tree: Any, mapper: Any): String {
    val writer = mapper.javaClass.getMethod("writerWithDefaultPrettyPrinter")
        .invoke(mapper)

    return writer.javaClass.getMethod("writeValueAsString", Any::class.java)
        .invoke(writer, tree) as String
}

private inline fun <T> invokeMapper(mapper: Any, action: String, block: () -> T): T = try {
    block()
} catch (e: NoSuchMethodException) {
    throw IllegalStateException(
        RestDocsErrorCode.NOT_A_JACKSON_MAPPER.message(
            "RestDocsSettings.extraction.objectMapper returned ${mapper.javaClass.name}, " +
                "which is not a Jackson ObjectMapper/JsonMapper.",
        ),
        e,
    )
} catch (
    // 리플렉션 호출을 감싼 예외일 뿐이다. 실제 원인(targetException)을 cause 로 넘긴다.
    @Suppress("SwallowedException")
    e: InvocationTargetException,
) {
    throw IllegalStateException("Could not $action JSON with ${mapper.javaClass.simpleName}.", e.targetException)
}
