package io.github.kardx.internal.openapi

import java.lang.reflect.Field
import java.lang.reflect.InaccessibleObjectException
import java.lang.reflect.Modifier

/**
 * epages 리소스 스니펫(`ResourceSnippet`)들이 JSON 매퍼 하나를 같이 쓰게 한다.
 *
 * epages(0.19 의 Jackson 2 `ObjectMapper`, 0.20 의 Jackson 3 `JsonMapper`)는 스니펫을 만들 때마다 매퍼를 새로 만든다.
 * 그래서 요청마다 직렬화기 캐시와 Kotlin 모듈의 리플렉션 캐시가 비어 있는 채로 `resource.json` 을 쓴다 (요청당 약 0.4 ms).
 * 스니펫 클래스마다 처음 만든 스니펫의 매퍼를 기억해 두고 뒤에 만든 스니펫의 매퍼 필드를 그것으로 바꾼다. epages 가 모든 스니펫에
 * 같은 설정으로 만드는 매퍼이고 직렬화에만 쓰므로 결과는 같고, Jackson 매퍼는 설정을 마친 뒤 여러 스레드가 같이 써도 안전하다.
 *
 * 바꿀 수 없으면(필드 이름·모양이 다른 epages 버전, 리플렉션 거부) 아무것도 하지 않고 epages 그대로 둔다.
 * JDK 26 부터는 final 필드를 리플렉션으로 바꾸면 경고를 남기므로(JEP 500) 그 버전에서는 바꾸지 않는다.
 */
internal object SharedResourceMapper {
    private const val FIELD_NAME = "objectMapper"

    /** final 필드 변경에 경고를 남기기 시작하는 JDK 버전 (JEP 500). */
    private const val WARNING_JDK = 26

    private val LOGGER: System.Logger = System.getLogger(SharedResourceMapper::class.java.name)

    /** 스니펫 클래스별 매퍼 필드와 공유 매퍼. 클래스 로더가 다르면(Jackson 클래스도 다르다) 따로 둔다. */
    private val SLOTS = object : ClassValue<Slot>() {
        override fun computeValue(type: Class<*>): Slot = Slot(mapperField(type))
    }

    @Volatile
    private var disabled = Runtime.version().feature() >= WARNING_JDK

    /** [snippet] 의 매퍼를 공유 매퍼로 바꾼다. 그 클래스에서 처음 부르면 [snippet] 의 매퍼가 공유 매퍼가 된다. [snippet] 을 돌려준다. */
    fun <T : Any> share(snippet: T): T {
        if (disabled) return snippet

        val slot = SLOTS.get(snippet.javaClass)
        val field = slot.field ?: return snippet

        try {
            val mapper = slot.mapper

            if (mapper == null) {
                slot.mapper = field.get(snippet)
            } else {
                field.set(snippet, mapper)
            }
        } catch (e: IllegalAccessException) {
            disable(e)
        } catch (e: IllegalArgumentException) {
            disable(e)
        }

        return snippet
    }

    /** 인스턴스 필드 `objectMapper`. 없거나 static 이거나 접근할 수 없으면 null. */
    private fun mapperField(type: Class<*>): Field? {
        val field = type.declaredFields
            .firstOrNull { it.name == FIELD_NAME && !Modifier.isStatic(it.modifiers) }
            ?: return null

        // 접근할 수 없으면 매퍼 공유는 최적화일 뿐이므로 epages 그대로 둔다
        return try {
            field.apply { isAccessible = true }
        } catch (e: InaccessibleObjectException) {
            inaccessible(type, e)
        } catch (e: SecurityException) {
            inaccessible(type, e)
        }
    }

    private fun inaccessible(type: Class<*>, cause: Exception): Field? {
        LOGGER.log(System.Logger.Level.DEBUG, "kardx: cannot access ${type.name}.$FIELD_NAME", cause)

        return null
    }

    private fun disable(cause: Exception) {
        disabled = true
        LOGGER.log(
            System.Logger.Level.DEBUG,
            "kardx: could not share the epages ResourceSnippet mapper; using one mapper per snippet",
            cause,
        )
    }

    private class Slot(val field: Field?) {
        @Volatile
        var mapper: Any? = null
    }
}
