package io.github.kardx.internal.introspection

import io.github.kardx.dsl.BOOLEAN
import io.github.kardx.dsl.DocsFieldType
import io.github.kardx.dsl.NUMBER
import io.github.kardx.dsl.OBJECT
import io.github.kardx.dsl.STRING
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * 값 타입의 JSON Schema 조각. REST Docs 표의 Type 열(JSON 타입)보다 자세한 정보를 OpenAPI·AsyncAPI 에 넘긴다.
 *
 * | 타입 | 조각 |
 * |---|---|
 * | `Int`, `Short`, `Byte` / `Long` / `BigInteger` | `integer` + `int32` / `int64` / 없음 |
 * | `Float` / `Double` / `BigDecimal` | `number` + `float` / `double` / 없음 |
 * | `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalDateTime`, `Date` | `string` + `date-time` |
 * | `LocalDate` / `LocalTime` / `Duration`·`Period` | `string` + `date` / `time` / `duration` (Boot 의 ISO-8601 문자열) |
 * | `UUID` / `URI`·`URL` / `ByteArray` | `string` + `uuid` / `uri` / `byte` (base64) |
 * | 값 배열·컬렉션 (`List<String>`, `Set<Level>`, `IntArray`) | `array` + 원소 `items` (Enum 이면 `items.enum`) |
 * | 값 맵 (`Map<String, Int>`) | `object` + `additionalProperties` |
 *
 * 프로퍼티의 `@JsonFormat(shape = ...)` 는 JSON 타입을 바꾸고, `@JsonFormat(pattern = ...)` 는 표준 형식이 아니므로 `format` 을 뺀다.
 * 하위 필드를 추출하는 객체와 구조를 알 수 없는 값(`Any`, `JsonNode`)은 null 이다.
 */
internal object TypeSchemas {
    fun of(type: KType, annotations: List<Annotation> = emptyList()): MutableMap<String, Any?>? {
        val schema = valueSchema(unwrap(type).type)
        val shape = JacksonAnnotations.jsonFormatType(annotations)

        return when {
            shape != null -> shaped(schema, shape, enumClassOf(unwrap(type).type) != null)
            JacksonAnnotations.jsonFormatPattern(annotations) != null -> schema?.apply { remove(FORMAT) }
            else -> schema
        }
    }

    /** [depth] 는 배열·맵을 몇 겹 들어왔는지. 원소가 다시 배열인 타입이 끝없이 이어질 수 있어 [MAX_NESTING] 에서 멈춘다. */
    private fun valueSchema(type: KType, depth: Int = 0): MutableMap<String, Any?>? {
        val k = type.classifier as? KClass<*> ?: return null

        return when (val shape = shapeOf(type)) {
            is Shape.Leaf -> leaf(k)
            is Shape.Array -> array(k, shape, depth)
            Shape.MapLike -> map(type, depth)
            else -> null
        }
    }

    private fun leaf(k: KClass<*>): MutableMap<String, Any?>? {
        if (k.java.isEnum) {
            return schema(enumJsonType(k.java))
        }

        SCALARS[k.javaObjectType.name]?.let { (type, format) ->
            return schema(type, format)
        }

        return when (docsFieldTypeOf(k)) {
            STRING -> schema(JsonTypes.STRING)
            NUMBER -> schema(JsonTypes.NUMBER)
            BOOLEAN -> schema(JsonTypes.BOOLEAN)
            OBJECT -> schema(JsonTypes.OBJECT)
            else -> null
        }
    }

    /** 원소가 값이면 `items` 를 붙인다. 객체 원소는 하위 필드가 스키마를 만든다. */
    private fun array(k: KClass<*>, shape: Shape.Array, depth: Int): MutableMap<String, Any?> {
        val primitive = k.java.componentType?.takeIf { it.isPrimitive }
        val items = shape.element?.let { items(it, depth) } ?: primitive?.let { leaf(it.kotlin) }

        return schema(JsonTypes.ARRAY).apply { items?.let { put(ITEMS, it) } }
    }

    private fun map(type: KType, depth: Int): MutableMap<String, Any?> {
        val values = type.arguments.lastOrNull()?.type?.let { items(it, depth) }

        return schema(JsonTypes.OBJECT).apply { values?.let { put("additionalProperties", it) } }
    }

    private fun items(element: KType, depth: Int): MutableMap<String, Any?>? {
        if (depth >= MAX_NESTING) {
            return null
        }

        val unwrapped = unwrap(element).type

        return valueSchema(unwrapped, depth + 1)?.apply { enumJsonValuesOf(unwrapped)?.let { put("enum", it) } }
    }

    /** `@JsonFormat(shape = NUMBER/STRING/BOOLEAN)` 로 바뀐 JSON 타입. 숫자가 된 Enum 은 순번(정수)이다. */
    private fun shaped(original: Map<String, Any?>?, shape: DocsFieldType, enum: Boolean): MutableMap<String, Any?> =
        when (shape) {
            STRING -> schema(JsonTypes.STRING)
            BOOLEAN -> schema(JsonTypes.BOOLEAN)
            else -> schema(if (enum || original?.get(TYPE) == JsonTypes.INTEGER) JsonTypes.INTEGER else NUMBER_TYPE)
        }

    private fun schema(type: String, format: String? = null): MutableMap<String, Any?> =
        linkedMapOf<String, Any?>(TYPE to type).apply { format?.let { put(FORMAT, it) } }

    const val TYPE: String = "type"
    const val FORMAT: String = "format"
    const val ITEMS: String = "items"

    private const val MAX_NESTING = 16
    private const val DATE_TIME = "date-time"
    private const val INT32 = "int32"
    private const val NUMBER_TYPE = JsonTypes.NUMBER

    /** Java 객체 타입 이름 → (JSON 타입, format). 원시 타입은 [KClass.javaObjectType] 으로 찾는다. */
    private val SCALARS: Map<String, Pair<String, String?>> = mapOf(
        "java.lang.Integer" to (JsonTypes.INTEGER to INT32),
        "java.lang.Short" to (JsonTypes.INTEGER to INT32),
        "java.lang.Byte" to (JsonTypes.INTEGER to INT32),
        "java.lang.Long" to (JsonTypes.INTEGER to "int64"),
        "java.math.BigInteger" to (JsonTypes.INTEGER to null),
        "java.lang.Float" to (JsonTypes.NUMBER to "float"),
        "java.lang.Double" to (JsonTypes.NUMBER to "double"),
        "java.math.BigDecimal" to (JsonTypes.NUMBER to null),
        "[B" to (JsonTypes.STRING to "byte"),
        "java.util.UUID" to (JsonTypes.STRING to "uuid"),
        "java.net.URI" to (JsonTypes.STRING to "uri"),
        "java.net.URL" to (JsonTypes.STRING to "uri"),
        "java.time.Instant" to (JsonTypes.STRING to DATE_TIME),
        "java.time.OffsetDateTime" to (JsonTypes.STRING to DATE_TIME),
        "java.time.ZonedDateTime" to (JsonTypes.STRING to DATE_TIME),
        "java.time.LocalDateTime" to (JsonTypes.STRING to DATE_TIME),
        "java.util.Date" to (JsonTypes.STRING to DATE_TIME),
        "java.util.Calendar" to (JsonTypes.STRING to DATE_TIME),
        "java.time.LocalDate" to (JsonTypes.STRING to "date"),
        "java.time.LocalTime" to (JsonTypes.STRING to "time"),
        "java.time.OffsetTime" to (JsonTypes.STRING to "time"),
        "java.time.Duration" to (JsonTypes.STRING to "duration"),
        "java.time.Period" to (JsonTypes.STRING to "duration"),
    )
}
