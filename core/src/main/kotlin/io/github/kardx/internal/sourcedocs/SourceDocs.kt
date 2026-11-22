package io.github.kardx.internal.sourcedocs

import io.github.kardx.diagnostics.RestDocsErrorCode
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * KDoc·Javadoc 에서 온 설명 ([SourceDocsIndex]). Gradle 플러그인이 색인 파일 경로를 시스템 속성 [PROPERTY] 로 넘긴다.
 *
 * 색인은 JVM 마다 처음 쓸 때 한 번 읽는다. 속성이 없으면 기능이 꺼진 것이고, 속성이 있는데 파일을 읽지 못하면
 * `io.github.kardx.config` 로거에 한 번 경고하고 끈다. 조회 함수마다 받는 `enabled`(호출하는 쪽이 설정
 * `DescriptionSettings.sourceDocs`, `kardx.source-docs.enabled` 를 넘긴다)가 false 면 색인이 있어도 쓰지 않는다.
 * 설정을 인자로 받는 것은 이 패키지가 `config` 에 기대지 않게 하려는 것이다 (`config` 의 기본 설명 함수가 이 객체를 부른다).
 *
 * 다른 설명(`@Description`, `description-annotations`, 지정한 Enum 설명 프로퍼티)이 없을 때만 쓰는 마지막 단계다.
 */
internal object SourceDocs {
    /** Gradle 플러그인의 `PluginConstants` 의 source docs 속성과 같다. */
    const val PROPERTY: String = "kardx.source-docs"

    private val LOGGER: System.Logger = System.getLogger("io.github.kardx.config")

    private const val ARITY_SEPARATOR = "/"

    @Volatile
    private var index: Lazy<SourceDocsIndex?> = newIndex()

    /** (선언 클래스, 소스 이름) → 프로퍼티 설명. 상위 타입까지 찾은 결과다 */
    private val properties = ConcurrentHashMap<Pair<Class<*>, String>, Optional<String>>()

    /** (설명을 찾을 클래스, 메서드 이름, 인자 수) → 메서드 설명. 핸들러 메서드마다 한 번만 찾는다 */
    private val methods = ConcurrentHashMap<Triple<List<String>, String, Int>, Optional<MethodDocs>>()

    /**
     * [owner] 의 소스 이름 [name] 프로퍼티 설명. [owner] 에 없으면 상위 클래스·인터페이스에서 찾는다
     * (상속한 프로퍼티, sealed 타입이 선언한 추상 프로퍼티). 없으면 null.
     */
    fun propertyDescription(enabled: Boolean, owner: Class<*>, name: String): String? {
        val docs = current(enabled) ?: return null

        return properties.computeIfAbsent(owner to name) {
            Optional.ofNullable(hierarchy(owner).firstNotNullOfOrNull { docs.type(it.name)?.properties?.get(name) })
        }.orElse(null)
    }

    /** [enumClass] 의 상수 [constant] 설명. 없으면 null. */
    fun enumConstantDescription(enabled: Boolean, enumClass: Class<*>, constant: String): String? = current(enabled)
        ?.type(enumClass.name)
        ?.enumConstants
        ?.get(constant)

    /**
     * 핸들러 메서드 [name] 의 설명. [classNames] 를 차례로 보고, 클래스마다 인자 수까지 맞춘 키(`fqcn#name/2`)를 먼저,
     * 없으면 `fqcn#name` 을 본다. 없으면 null.
     */
    fun method(enabled: Boolean, classNames: List<String>, name: String, arity: Int): MethodDocs? {
        val docs = current(enabled) ?: return null

        return methods.computeIfAbsent(Triple(classNames, name, arity)) {
            val found = classNames.asSequence()
                .flatMap { sequenceOf("$it#$name$ARITY_SEPARATOR$arity", "$it#$name") }
                .firstNotNullOfOrNull(docs::method)

            Optional.ofNullable(found)
        }.orElse(null)
    }

    /** 테스트용: 색인과 조회 결과를 버린다. 다음 조회가 시스템 속성을 다시 읽는다. */
    fun reset() {
        index = newIndex()
        properties.clear()
        methods.clear()
    }

    private fun current(enabled: Boolean): SourceDocsIndex? = if (enabled) index.value else null

    private fun newIndex(): Lazy<SourceDocsIndex?> = lazy { load(System.getProperty(PROPERTY)) }

    private fun load(path: String?): SourceDocsIndex? {
        if (path.isNullOrBlank()) return null

        return runCatching { SourceDocsIndex.parse(File(path).readText()) }
            .onFailure {
                LOGGER.log(
                    System.Logger.Level.WARNING,
                    RestDocsErrorCode.SOURCE_DOCS_UNREADABLE.message(
                        "Could not read the source docs index '$path' ($PROPERTY), so KDoc/Javadoc descriptions are " +
                            "not used: ${it.message}",
                    ),
                )
            }
            .getOrNull()
    }

    /** [type] 과 그 상위 클래스·인터페이스. 가까운 것부터, `Object` 는 뺀다. */
    private fun hierarchy(type: Class<*>): List<Class<*>> {
        val ordered = LinkedHashSet<Class<*>>()
        val pending = ArrayDeque(listOf(type))

        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()

            if (next != Any::class.java && ordered.add(next)) {
                pending.addAll(listOfNotNull(next.superclass) + next.interfaces)
            }
        }

        return ordered.toList()
    }
}
