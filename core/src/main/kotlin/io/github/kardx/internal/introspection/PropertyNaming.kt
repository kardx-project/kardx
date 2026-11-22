package io.github.kardx.internal.introspection

import io.github.kardx.config.PropertyNameInput
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.serializationConfigOf
import io.github.kardx.internal.reflect.memberValue
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Jackson 의 프로퍼티 이름 전략(`SNAKE_CASE` 등)을 문서 필드 이름에 적용한다.
 *
 * 우선순위는 Jackson 과 같다.
 * 1. `@JsonProperty("name")` 으로 명시한 이름은 바꾸지 않는다
 * 2. 클래스의 `@JsonNaming(...)` 전략
 * 3. [io.github.kardx.config.ExtractionSettings.propertyNameStrategy], 없으면 매퍼에 설정된 전역 전략
 *
 * 전략은 리플렉션으로 부른다. Jackson 3 의 `NamingBase.translate(String)` 은 protected 라서 Jackson 2·3 에 모두 있는
 * public `nameForField(config, field, defaultName)` 을 먼저 쓰고(`NamingBase` 는 앞의 두 인자를 보지 않는다),
 * 그 메서드가 인자를 요구해 실패하는 직접 만든 전략이면 public `translate` 로 대신한다.
 */
internal object PropertyNaming {
    private val classStrategies = ConcurrentHashMap<KClass<*>, Translator>()

    /** 클래스나 상위 타입에 붙은 `@JsonNaming` 전략. 없으면 null. */
    fun ofClass(k: KClass<*>): ((String) -> String)? =
        classStrategies.getOrPut(k) { Translator(jsonNamingOf(k)?.let(::translatorOf)) }.translate

    /** 설정 또는 매퍼의 전역 전략. 없으면 null. */
    fun global(): ((String) -> String)? = RestDocsConfiguration.current.extraction.propertyNameStrategy
        ?.let { strategy -> { name: String -> strategy.translate(PropertyNameInput(name)) } }
        // 매퍼를 만들 수 없거나 전략을 리플렉션으로 읽지 못하면 이름을 바꾸지 않는다
        ?: runCatching { mapperStrategy(RestDocsConfiguration.objectMapper()) }.getOrNull()

    private fun jsonNamingOf(k: KClass<*>): Class<*>? = generateSequence(k.java) { it.superclass }
        .flatMap { sequenceOf(it) + it.interfaces.asSequence() }
        .flatMap { it.annotations.asSequence() }
        .firstOrNull { it.annotationClass.java.name in JSON_NAMING }
        ?.let { it.memberValue() as? Class<*> }

    private fun mapperStrategy(mapper: Any): ((String) -> String)? {
        val direct = findMethod(mapper.javaClass, GET_STRATEGY)?.invoke(mapper)
        val strategy = direct
            ?: serializationConfigOf(mapper)?.let { config ->
                findMethod(config.javaClass, GET_STRATEGY)?.invoke(config)
            }

        return strategy?.let(::translatorOfInstance)
    }

    /** [strategyClass] 를 기본 생성자로 만든 전략. 만들 수 없으면(생성자 없음·생성 실패) Jackson 처럼 전략 없이 둔다. */
    private fun translatorOf(strategyClass: Class<*>): ((String) -> String)? = runCatching {
        translatorOfInstance(
            strategyClass.getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance(),
        )
    }.getOrNull()

    private fun translatorOfInstance(strategy: Any): ((String) -> String)? {
        val nameForField = strategy.javaClass.methods.firstOrNull {
            it.name == NAME_FOR_FIELD && it.parameterCount == NAME_FOR_FIELD_PARAMETERS
        }

        val translate = findMethod(strategy.javaClass, TRANSLATE, String::class.java)

        if (nameForField == null && translate == null) {
            return null
        }

        return { name ->
            val viaField = nameForField?.let { method ->
                runCatching { method.invoke(strategy, null, null, name) as? String }.getOrNull()
            }

            viaField ?: translate?.invoke(strategy, name) as? String ?: name
        }
    }

    /** public 메서드. 없으면(NoSuchMethodException) null. Jackson 2/3 에 따라 있는 메서드가 다르다. */
    private fun findMethod(type: Class<*>, name: String, vararg parameters: Class<*>): Method? =
        runCatching { type.getMethod(name, *parameters) }.getOrNull()

    private class Translator(val translate: ((String) -> String)?)

    private const val GET_STRATEGY = "getPropertyNamingStrategy"
    private const val NAME_FOR_FIELD = "nameForField"
    private const val NAME_FOR_FIELD_PARAMETERS = 3
    private const val TRANSLATE = "translate"

    private val JSON_NAMING = setOf(
        "com.fasterxml.jackson.databind.annotation.JsonNaming",
        "tools.jackson.databind.annotation.JsonNaming",
    )
}
