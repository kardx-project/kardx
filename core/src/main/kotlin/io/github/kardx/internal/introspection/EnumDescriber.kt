package io.github.kardx.internal.introspection

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.ENUM
import io.github.kardx.dsl.NUMBER
import io.github.kardx.internal.reflect.enumJsonValueClass
import io.github.kardx.internal.reflect.enumJsonValues
import io.github.kardx.internal.reflect.enumPropertyOf
import io.github.kardx.internal.sourcedocs.SourceDocs
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Enum 필드 설명 뒤에 값 목록을 문단으로 붙인다. 값은 [enumJsonValues] (JSON 에 실제로 나가는 값),
 * 설명은 [io.github.kardx.config.ExtractionSettings.extraction.enumDescriptionProperty] 프로퍼티에서 읽는다
 * (필드 → getter → 같은 이름의 메서드, [enumPropertyOf]). 그 값이 없으면 상수의 KDoc·Javadoc 설명([SourceDocs])이다.
 * 표 칸 안에서 일반 개행은 한 줄로 합쳐지므로 빈 줄로 구분한다.
 */
internal object EnumDescriber {
    fun describe(enumClass: Class<*>): String {
        val property = enumPropertyOf(enumClass, RestDocsConfiguration.current.extraction.enumDescriptionProperty)
        val values = enumJsonValues(enumClass)
        val sourceDocsEnabled = RestDocsConfiguration.current.descriptions.sourceDocs
        val lines = enumClass.enumConstants.orEmpty()
            .filterIsInstance<Enum<*>>()
            .mapIndexed { index, constant ->
                // 설명 프로퍼티도 KDoc·Javadoc 설명도 없는 상수는 값만 나열한다
                val label = property?.let { runCatching { it(constant) }.getOrNull() }
                    ?: SourceDocs.enumConstantDescription(sourceDocsEnabled, enumClass, constant.name)

                if (label == null) values[index] else "${values[index]} : $label"
            }

        return if (lines.isEmpty()) {
            ""
        } else {
            lines.joinToString(separator = "\n\n", prefix = "\n\n")
        }
    }
}

/** [type] 이 Enum 이면 그 클래스, 아니면 null. */
internal fun enumClassOf(type: KType): Class<*>? = (type.classifier as? KClass<*>)?.java?.takeIf { it.isEnum }

/** Enum 타입이면 JSON 값 목록, 아니면 null. */
internal fun enumJsonValuesOf(type: KType): List<String>? = enumClassOf(type)?.let(::enumJsonValues)

/**
 * Enum 값의 JSON Schema 타입. `@JsonValue` 가 정수를 돌려주면 `integer`, 그 밖의 숫자는 `number`, 불리언이면 `boolean`,
 * 그 밖에는 `string` (상수 이름, `@JsonProperty`, 문자열 `@JsonValue`).
 */
internal fun enumJsonType(enumClass: Class<*>): String {
    val value = enumJsonValueClass(enumClass)?.kotlin?.javaObjectType ?: return JsonTypes.STRING

    return when {
        value.name in INTEGER_CLASSES -> JsonTypes.INTEGER
        Number::class.java.isAssignableFrom(value) -> JsonTypes.NUMBER
        value == Boolean::class.javaObjectType -> JsonTypes.BOOLEAN
        else -> JsonTypes.STRING
    }
}

/** Enum 필드의 REST Docs 타입. 숫자·불리언 `@JsonValue` 면 그 JSON 타입으로 검증하고, 그 밖에는 문자열([ENUM])이다. */
internal fun enumDocsFieldType(enumClass: Class<*>): DocsFieldType = when (enumJsonType(enumClass)) {
    JsonTypes.INTEGER, JsonTypes.NUMBER -> NUMBER
    JsonTypes.BOOLEAN -> BOOLEAN
    else -> ENUM
}

private val INTEGER_CLASSES = setOf(
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Short",
    "java.lang.Byte",
    "java.math.BigInteger",
)
