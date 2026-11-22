package io.github.kardx.internal.introspection

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.serializerOrNull
import java.util.Optional
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * `@Serializable` 클래스 하나의 serial descriptor 에서 읽은 정보.
 *
 * @property serialName 클래스의 serial 이름 (`@SerialName`, 없으면 전체 이름). sealed 판별자 값이다
 * @property elements 객체로 직렬화되면 그 필드 (선언 순서). 값 하나로 직렬화되면 빈 목록
 * @property valueType 값 하나(문자열·숫자·불리언)로 직렬화되면 그 Kotlin 타입, 아니면 null
 */
internal class SerialClass(val serialName: String, val elements: List<SerialElement>, val valueType: KType?)

/**
 * 객체 필드 하나.
 *
 * @property name JSON 키 (`@SerialName` 반영)
 * @property optional 입력에서 생략할 수 있다 (기본값이 있고 `@Required` 가 아니다)
 * @property valueType 필드 직렬화기가 값 하나로 쓰면 그 Kotlin 타입 (`@Serializable(with = ...)`), 아니면 null
 */
internal class SerialElement(val name: String, val optional: Boolean, val valueType: KType?)

/**
 * kotlinx.serialization API 를 직접 부르는 유일한 곳. [KotlinxSerialization] 이 클래스패스를 확인한 뒤에만 로드한다.
 * descriptor 는 클래스마다 한 번만 만든다.
 */
@OptIn(ExperimentalSerializationApi::class)
internal object KotlinxDescriptors {
    private val classes = object : ClassValue<Optional<SerialClass>>() {
        override fun computeValue(type: Class<*>): Optional<SerialClass> = Optional.ofNullable(compute(type.kotlin))
    }

    /** [k] 의 descriptor 정보. 직렬화기를 찾지 못하거나 다형(`OPEN`·`SEALED`) 직렬화기면 null. */
    fun of(k: KClass<*>): SerialClass? = classes.get(k.java).orElse(null)

    private fun compute(k: KClass<*>): SerialClass? {
        // 타입 인자는 필드 이름·생략 여부에 영향을 주지 않으므로 아무 직렬화 가능한 타입(String)으로 채운다
        val type = k.createType(k.typeParameters.map { KTypeProjection.invariant(typeOf<String>()) })

        // 직렬화기를 만들지 못하는 클래스(필수 인자가 있는 사용자 직렬화기 등)는 Jackson 규칙으로 돌아간다
        val descriptor = runCatching { serializerOrNull(type)?.descriptor }.getOrNull() ?: return null
        val kind = descriptor.kind

        return when {
            kind is PrimitiveKind -> SerialClass(descriptor.serialName, emptyList(), primitiveType(kind))

            kind == StructureKind.CLASS || kind == StructureKind.OBJECT ->
                SerialClass(descriptor.serialName, elements(descriptor), null)

            else -> null
        }
    }

    private fun elements(descriptor: SerialDescriptor): List<SerialElement> = (0 until descriptor.elementsCount).map {
        val kind = descriptor.getElementDescriptor(it).kind

        SerialElement(
            descriptor.getElementName(it),
            descriptor.isElementOptional(it),
            (kind as? PrimitiveKind)?.let(::primitiveType),
        )
    }

    private fun primitiveType(kind: SerialKind): KType = when (kind) {
        PrimitiveKind.BOOLEAN -> typeOf<Boolean>()
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT -> typeOf<Int>()
        PrimitiveKind.LONG -> typeOf<Long>()
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> typeOf<Double>()
        else -> typeOf<String>()
    }
}
