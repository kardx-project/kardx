package io.github.kardx.config

import io.github.kardx.annotation.Description
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

/**
 * [RestDocsSettings] 의 가변 프로퍼티 하나. 묶음(`openApi` 등) 안의 것은 [group] 으로 묶음 객체를 꺼낸 뒤 [property] 를 읽고 쓴다.
 * 루트의 것(`language`)은 [group] 이 설정 자신을 돌려준다.
 */
private class SettingsProperty(
    val name: String,
    val group: (RestDocsSettings) -> Any,
    val property: KMutableProperty1<Any, Any?>,
) {
    val returnType: KType get() = property.returnType

    fun get(settings: RestDocsSettings): Any? = property.get(group(settings))

    // private set(`document.order`)도 copy 가 옮기는지 보려고 접근을 연다
    fun set(settings: RestDocsSettings, value: Any?) = property.setter
        .apply { isAccessible = true }
        .call(group(settings), value)
}

@Suppress("UNCHECKED_CAST")
private fun mutableProperties(type: KClass<*>): List<KMutableProperty1<Any, Any?>> =
    type.memberProperties.filterIsInstance<KMutableProperty1<*, *>>()
        .map { it as KMutableProperty1<Any, Any?> }

/** 설정 묶음(`openApi`, `masking` …)인지. 묶음 객체는 바꿀 수 없고(private set) 안의 프로퍼티를 바꾼다. */
private fun KProperty1<*, *>.isGroup(): Boolean = returnType.jvmErasure.java.name.endsWith("Settings")

/** [RestDocsSettings] 와 그 묶음의 모든 가변 프로퍼티. 새 프로퍼티나 묶음을 더하면 자동으로 들어온다. */
@Suppress("UNCHECKED_CAST")
private val SETTINGS_PROPERTIES: List<SettingsProperty> =
    mutableProperties(RestDocsSettings::class).filterNot { it.isGroup() }
        .map { SettingsProperty(it.name, { settings -> settings }, it) } +
        RestDocsSettings::class.memberProperties.filter { it.isGroup() }
            .flatMap { group ->
                val read = group as KProperty1<RestDocsSettings, Any>

                mutableProperties(group.returnType.jvmErasure).map { property ->
                    SettingsProperty("${group.name}.${property.name}", { settings -> read.get(settings) }, property)
                }
            }

/** 같은 값인지. 함수·인터페이스 값은 equals 가 동일성 비교라 같은 인스턴스여야 한다. */
private fun sameValue(a: Any?, b: Any?): Boolean = a == b

/** [default] 와 다른 값. 처리할 수 없는 타입이면 실패해서 규칙을 더하게 한다. */
private fun changedValue(property: SettingsProperty, default: Any?): Any {
    val type = property.returnType
    val erasure = type.jvmErasure

    return when {
        erasure == Boolean::class -> !(default as Boolean)
        erasure == Int::class -> (default as Int) + 1
        erasure == String::class -> "changed-${property.name}"
        erasure == Set::class -> if ((default as Set<*>).isEmpty()) setOf(sampleElement(type)) else emptySet()
        erasure == List::class -> if ((default as List<*>).isEmpty()) listOf(sampleElement(type)) else emptyList()
        erasure == ResponseWrapper::class -> ResponseWrapper(String::class, "changed")
        erasure.java.isEnum -> erasure.java.enumConstants.first { it != default }
        erasure.java.isInterface -> proxyOf(erasure)
        else -> error("${property.name}: $type 에 쓸 값 규칙이 없다. 이 테스트에 규칙을 더한다")
    }
}

private fun sampleElement(collectionType: KType): Any {
    val element = requireNotNull(collectionType.arguments.single().type).jvmErasure

    return when {
        element == String::class -> "changed"
        element.java.isEnum -> element.java.enumConstants.first()
        element == DescriptionAnnotation::class -> DescriptionAnnotation.of(Description::class)
        element == MaskedAnnotation::class -> MaskedAnnotation.of(Description::class)
        element == SnippetFile::class -> SnippetFile.custom("changed", "Changed")
        element.java.isInterface -> proxyOf(element)
        else -> error("$collectionType 의 원소 값 규칙이 없다")
    }
}

/** 함수 타입·인터페이스(HeaderMasker 등)의 새 인스턴스. 호출되지 않으므로 아무것도 하지 않는다. */
private fun proxyOf(type: KClass<*>): Any =
    Proxy.newProxyInstance(type.java.classLoader, arrayOf(type.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "proxy of ${type.simpleName}"
            else -> null
        }
    }

class RestDocsSettingsCopyTest :
    BehaviorSpec({
        given("모든 가변 프로퍼티를 기본값과 다른 값으로 바꾼 설정") {
            val defaults = RestDocsSettings()
            val changed = SETTINGS_PROPERTIES.associateWith { changedValue(it, it.get(defaults)) }
            val settings = RestDocsSettings().apply {
                changed.forEach { (property, value) -> property.set(this, value) }
            }

            then("묶음마다 가변 프로퍼티를 찾는다") {
                SETTINGS_PROPERTIES.map { it.name } shouldContain "openApi.enabled"
                SETTINGS_PROPERTIES.map { it.name } shouldContain "language"
            }

            then("검사할 프로퍼티가 있고, 바꾼 값은 기본값과 다르다") {
                SETTINGS_PROPERTIES.shouldNotBeEmpty()
                changed.forEach { (property, value) ->
                    withClue(property.name) { sameValue(value, property.get(defaults)) shouldBe false }
                }
            }

            then("copy() 는 모든 가변 프로퍼티를 복사한다 (새 프로퍼티를 copy 에 빠뜨리면 실패)") {
                val copy = settings.copy()

                changed.forEach { (property, value) ->
                    withClue(property.name) { sameValue(property.get(copy), value) shouldBe true }
                }
            }

            then("copy() 의 묶음은 원본과 다른 객체라 복사본을 바꿔도 원본이 그대로다") {
                val copy = settings.copy()

                copy.openApi.enabled = !settings.openApi.enabled

                copy.openApi shouldNotBeSameInstanceAs settings.openApi
                copy.openApi.enabled shouldBe !settings.openApi.enabled
            }
        }
    })
