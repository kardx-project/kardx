package io.github.kardx.internal.introspection

import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 여러 하위 타입 중 하나로 직렬화되는 타입 (OpenAPI `oneOf`).
 *
 * @property base 선언 타입 (`Payment`)
 * @property variants 실제 하위 타입. `@JsonSubTypes` 선언 순서, 선언에 없으면 sealed 선언 순서다
 * @property discriminator 하위 타입을 가리는 판별자. 표현할 수 없으면 null
 */
internal class PolymorphicType(
    val base: KClass<*>,
    val variants: List<KClass<*>>,
    val discriminator: TypeDiscriminator?,
)

/**
 * `@JsonTypeInfo` 판별자.
 *
 * @property property 판별자 프로퍼티 이름 (`type`)
 * @property names 하위 타입 → 판별자 값 (`CardPayment` → `CARD`)
 * @property existing 하위 타입에 이미 있는 프로퍼티인지 (`include = EXISTING_PROPERTY`). 아니면 Jackson 이 더한다
 */
internal class TypeDiscriminator(val property: String, val names: Map<KClass<*>, String>, val existing: Boolean)

/**
 * 다형 타입을 찾는다. Kotlin sealed 클래스·인터페이스(하위 타입이 있을 때)와 `@JsonSubTypes` 를 선언한 추상 타입·인터페이스다.
 * 하위 타입을 감싸는 `@JsonTypeInfo(include = WRAPPER_OBJECT/WRAPPER_ARRAY)` 는 하위 타입 스키마 중 하나로 나타낼 수 없어
 * 다형 타입으로 보지 않는다. 클래스마다 한 번만 계산한다.
 */
internal object PolymorphicTypes {
    private val cache = ConcurrentHashMap<KClass<*>, Optional<PolymorphicType>>()

    /** [k] 가 다형 타입이면 그 정보. 아니면 null. */
    fun of(k: KClass<*>): PolymorphicType? = cache.computeIfAbsent(k) { Optional.ofNullable(compute(it)) }
        .orElse(null)

    private fun compute(k: KClass<*>): PolymorphicType? {
        val leaves = when {
            k.isSealed -> PropertyIntrospector.leafSubclasses(k)
            k.isAbstract || k.java.isInterface -> SealedTypeDiscriminator.declaredSubclasses(k)
            else -> emptyList()
        }

        if (leaves.isEmpty() || SealedTypeDiscriminator.isWrapped(k)) {
            return null
        }

        val variants = SealedTypeDiscriminator.ordered(k, leaves)

        val discriminator = if (k.isSealed && KotlinxSerialization.applies(k)) {
            KotlinxSerialization.typeDiscriminator(k, variants)
        } else {
            SealedTypeDiscriminator.discriminator(k, variants)
        }

        return PolymorphicType(k, variants, discriminator)
    }
}
