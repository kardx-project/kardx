package io.github.kardx.config

import io.github.kardx.internal.jackson.DefaultObjectMapper
import org.springframework.core.env.PropertyResolver
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 문서화 설정의 진입점. Spring `@Configuration` 클래스가 아니라 테스트 코드에서 직접 부르는 싱글턴 객체이며,
 * 빈으로 등록하거나 `@Import` 하지 않는다.
 *
 * - [configure]: 모든 테스트의 기본값을 바꾼다. 테스트 기본 클래스나 JUnit 확장에서 한 번 호출한다.
 *   [AutoRestDocumentationExtension][io.github.kardx.junit.AutoRestDocumentationExtension]·Kotest `RestDocsExtension` 을
 *   쓰면 테스트 실행 중(`@BeforeEach`, 테스트 본문, `@AfterEach`)에 더한 블록은 그 테스트가 끝날 때 그 블록만 빠진다
 *   (`@BeforeAll`·클래스 밖에서 더한 블록과 다른 테스트가 더한 블록은 그대로)
 * - [withSettings]: 블록 안에서만 다른 값을 쓴다. 현재 스레드에만 적용되므로 병렬 테스트에서도 안전하다
 * - [current]: 지금 적용 중인 값 (범위 설정이 있으면 그 값, 없으면 기본값). 읽기 전용으로 쓴다. 바꾸려면 [configure]
 *
 * `application.yml` 의 `kardx.*` 속성으로도 기본값을 정할 수 있다. 우선순위는
 * 라이브러리 기본값 < 속성(yml, 시스템 속성, 환경 변수) < [configure] < [withSettings] 이다.
 * [reset] 은 [configure] 로 바꾼 값만 지우고 속성 값은 남긴다.
 *
 * 병렬 실행 제한: [configure] 로 만든 기본값은 JVM 전역 하나다. 테스트가 끝날 때 그 테스트가 더한 블록만 빼므로 동시에 도는
 * 다른 테스트의 설정을 지우거나 되살리지는 않지만, 동시에 도는 동안에는 두 테스트가 더한 블록이 함께 적용된 값을 본다.
 * 그래서 서로 다른 [configure] 값으로 **동시에 문서화**하면 스니펫·문서에 상대 테스트의 설정이 섞일 수 있다. 테스트마다 다른 값은
 * [withSettings] 로 주거나(현재 스레드에만 적용) 그런 테스트를 차례로 돌린다. 클래스 문서(`.adoc`)는 그 클래스에서 마지막으로
 * 실행한 테스트가 본 설정으로 만들므로, 그때 다른 테스트가 더한 블록이 있었다면 그 값도 들어간다.
 * 테스트에 붙지 않은 스레드(테스트가 직접 만든 스레드·스레드 풀, MockMvc 비동기·서버 스레드 등)에서 부른 [configure] 는
 * 어느 테스트의 것도 아니므로 전역에 남는다. Kotest 는 테스트 코루틴과 그 자식 코루틴이 테스트에 붙는다.
 *
 * 속성(yml) 값은 테스트의 Spring 컨텍스트(Environment)에서 읽어 JVM 전역 기본값 하나에 반영한다.
 * `kardx.*` 값이 서로 다른 컨텍스트(프로파일, `@TestPropertySource` 등)의 테스트 클래스를 **병렬로** 돌리면
 * 나중에 읽은 컨텍스트의 값으로 다른 클래스의 문서가 만들어질 수 있다. 그런 클래스는 같은 스레드에서 차례로 돌리거나
 * (`@Execution(ExecutionMode.SAME_THREAD)`, `@ResourceLock`), 클래스마다 다른 값은 yml 대신 [withSettings] 로 준다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     extraction { responseWrapper = ResponseWrapper(ApiResponse::class, dataPath = "data") }
 *     document { commonIncludeFiles = listOf("landing.adoc") }
 * }
 *
 * RestDocsConfiguration.withSettings({ extraction.arrayElementFieldsOptional = true }) {
 *     mockMvc.perform(get("/api/products?keyword=none"))
 *         .andDocument { ... }
 * }
 * ```
 */
public object RestDocsConfiguration {
    /** 속성(yml, 시스템 속성)에서 읽은 설정. null 이면 시스템 속성만 읽은 [systemExternal]. */
    @Volatile
    private var external: (RestDocsSettings.() -> Unit)? = null

    /** 시스템 속성·환경 변수 설정. 값이 잘못됐으면 처음 쓸 때 속성 이름을 담은 예외가 난다 (클래스 초기화 실패가 아니라). */
    private val systemExternal: RestDocsSettings.() -> Unit by lazy {
        ExternalSettings.from(ExternalSettings.systemSource())
    }

    /** 마지막으로 속성을 읽은 Spring Environment. 같은 Environment 를 다시 읽지 않는다. */
    @Volatile
    private var externalSource: Any? = null

    /** [configure] 로 받은 블록. 속성을 다시 읽으면 그 위에 다시 적용한다. */
    private val configured = CopyOnWriteArrayList<Registration>()

    /** 이 스레드에서 실행 중인 테스트의 [Changes]. [configure] 가 더한 블록을 여기에 적는다. 없으면 전역 설정이다 */
    private val tracking = ThreadLocal<Changes?>()

    /** 전역 기본값. 바꿀 때마다 새 객체로 교체하므로(copy-on-write) 다른 스레드가 반쯤 바뀐 값을 읽지 않는다. */
    @Volatile
    private var defaults: RestDocsSettings? = null
    private val scoped = ThreadLocal<RestDocsSettings?>()

    /** 이 스레드에서 `restDocsMockMvc`·`restDocsWebTestClient` 가 찾은 애플리케이션 매퍼 빈 */
    private val contextObjectMapper = InheritableThreadLocal<Any?>()

    /** 지금 적용 중인 설정. 읽기 전용으로 쓴다. */
    public val current: RestDocsSettings get() = scoped.get() ?: defaults ?: synchronized(this) {
        defaults ?: buildDefaults().also { defaults = it }
    }

    /**
     * 전역 기본값을 바꾼다. [withSettings] 블록 안에서 호출하면 그 범위의 값만 바꾼다.
     * [block] 이 예외를 던지면 등록하지 않고 기본값도 그대로 둔 채 예외를 그대로 던진다.
     *
     * [block] 은 기억해 두었다가 속성(yml)을 다시 읽거나 다른 블록을 더할 때마다 새 설정 객체에 처음부터 다시 적용한다.
     * 그래서 receiver([RestDocsSettings])만 바꾸고, 카운터 증가·파일 쓰기·외부 상태 변경 같은 부수효과는 없어야 한다.
     *
     * 테스트 실행 중(JUnit·Kotest 확장이 그 스레드에 테스트를 붙인 동안)에 부르면 그 테스트가 더한 블록으로 기억했다가
     * 테스트가 끝날 때 이 블록만 뺀다. 같은 블록 객체를 여러 번 더해도 호출마다 따로 기억한다.
     */
    public fun configure(block: RestDocsSettings.() -> Unit) {
        val scope = scoped.get()

        if (scope != null) {
            scope.block()

            return
        }

        val registration = Registration(block)

        synchronized(this) {
            // 처음부터 다시 만든다. 기본값이 아직 없을 때 block 이 두 번 적용되지 않는다.
            // 새 기본값을 다 만든 뒤에 반영하므로 block 이 실패하면 아무것도 바뀌지 않는다
            val next = buildDefaults(registrations = configured + registration)

            configured += registration
            defaults = next
        }

        tracking.get()?.record(registration)
    }

    /** [block] 에서 바꾼 값을 [action] 안에서만 현재 스레드에 적용한다. */
    public fun <T> withSettings(block: RestDocsSettings.() -> Unit, action: () -> T): T = using(
        settings = current.copy().apply(block),
        action = action,
    )

    /** 지금 설정의 복사본. 나중에 [using] 으로 같은 설정을 다시 적용할 때 쓴다. */
    internal fun snapshot(): RestDocsSettings = current.copy()

    /** [settings] 를 [action] 안에서만 현재 스레드에 적용한다. */
    internal fun <T> using(settings: RestDocsSettings, action: () -> T): T {
        val previous = scoped.get()

        scoped.set(settings)

        try {
            return action()
        } finally {
            scoped.set(previous)
        }
    }

    /**
     * [configure] 로 더한 블록을 모두 지운다. 속성(yml 등)에서 읽은 값은 남는다.
     *
     * 누가 언제 더했든(`@BeforeAll`·클래스 밖·다른 테스트 포함) JVM 전역 기본값의 블록을 모두 지우고, 지운 값은 테스트가 끝나도
     * 되살아나지 않는다. 그래서 테스트 본문이나 `@AfterEach` 에서 부르면 같은 클래스의 다음 테스트에도 `@BeforeAll` 설정이 없고,
     * 동시에 도는 다른 테스트의 설정도 지운다.
     *
     * [AutoRestDocumentationExtension][io.github.kardx.junit.AutoRestDocumentationExtension]·Kotest `RestDocsExtension` 을
     * 쓰면 테스트 실행 중에 더한 블록은 그 테스트가 끝날 때 저절로 빠지므로 `@AfterEach` 에서 부를 필요가 없다.
     * 클래스(`@BeforeAll`·`companion object`) 수준에서 한 설정까지 지우고 싶을 때, 또는 그 확장 없이 테스트할 때 쓴다.
     */
    public fun reset() {
        synchronized(this) {
            val next = buildDefaults(registrations = emptyList())

            configured.clear()
            defaults = next
        }

        scoped.remove()
        contextObjectMapper.remove()
    }

    /** [configure] 로 더한 블록 하나. 같은 블록 객체를 여러 번 더해도 더한 횟수만큼 따로 뺄 수 있게 감싼다. */
    internal class Registration(val block: RestDocsSettings.() -> Unit)

    /**
     * 테스트 실행 하나가 [configure] 로 더한 블록. [track] 으로 스레드에 붙인 동안 더한 블록을 기억했다가 [revert] 로 그 블록만 뺀다.
     * 다른 실행이나 실행 밖에서 더한 블록, [reset] 으로 이미 지워진 블록은 건드리지 않는다.
     */
    internal class Changes {
        private val added = CopyOnWriteArrayList<Registration>()

        internal fun record(registration: Registration) {
            added += registration
        }

        /** 이 실행이 더한 블록을 뺀다. 남은 블록이 그대로면 기본값을 다시 만들지 않는다. 두 번 불러도 된다. */
        fun revert() {
            val removed = added.toList()

            added.clear()
            remove(removed)
        }
    }

    /**
     * 이 스레드에서 부르는 [configure] 를 [changes] 에 기억하게 한다. 돌려받은 이전 값을 다시 넘겨 되돌린다.
     * `null` 을 넘기면 이 스레드의 [configure] 는 전역 설정이 된다.
     */
    internal fun track(changes: Changes?): Changes? = tracking.get()
        .also { tracking.set(changes) }

    private fun remove(registrations: List<Registration>) {
        if (registrations.isEmpty()) return

        synchronized(this) {
            val remaining = configured.filter { registration -> registrations.none { it === registration } }

            if (remaining.size != configured.size) {
                val next = buildDefaults(registrations = remaining)

                configured.retainAll(remaining.toSet())
                defaults = next
            }
        }
    }

    /**
     * Spring `Environment` 의 `kardx.*` 속성을 기본값에 반영한다. 같은 Environment 는 한 번만 읽는다.
     * [configure] 로 바꾼 값은 그대로 유지된다. 실패하면 이전 속성 값이 그대로 남는다.
     *
     * 기본값은 JVM 전역 하나이므로 Environment 별로 따로 보관하지 않고 마지막에 읽은 Environment 의 값으로 **교체**한다.
     * 값이 다른 컨텍스트의 테스트 클래스를 병렬로 돌리면 한 클래스의 문서가 다른 클래스의 yml 값으로 만들어질 수 있다
     * (클래스 문서 설명의 "병렬 실행 제한" 참고). 차례로 실행하면 클래스마다 자기 컨텍스트를 다시 읽으므로 문제없다.
     */
    internal fun applyEnvironment(environment: PropertyResolver) {
        if (externalSource === environment) return

        val settings = ExternalSettings.from { key -> environment.getProperty(key) }

        synchronized(this) {
            val next = buildDefaults(externalBlock = settings)

            external = settings
            externalSource = environment
            defaults = next
        }
    }

    /** 속성 값을 시스템 속성 기준으로 되돌린다. 테스트용. */
    internal fun clearEnvironment() {
        synchronized(this) {
            val next = buildDefaults(externalBlock = null)

            external = null
            externalSource = null
            defaults = next
        }
    }

    /**
     * 기본값을 새로 만든다. 상태는 바꾸지 않으므로 호출하는 쪽이 성공한 뒤에 반영한다
     * (실패한 설정이 남아 이후의 모든 호출을 깨뜨리지 않도록).
     */
    private fun buildDefaults(
        externalBlock: (RestDocsSettings.() -> Unit)? = external,
        registrations: List<Registration> = configured,
    ): RestDocsSettings = RestDocsSettings().apply(externalBlock ?: systemExternal)
        .also { settings ->
            registrations.forEach { settings.(it.block)() }
        }

    /** 이 스레드의 애플리케이션 매퍼 빈. `restDocsMockMvc`·`restDocsWebTestClient` 가 찾아 둔다. 없으면 null. */
    internal var threadObjectMapper: Any?
        get() = contextObjectMapper.get()
        set(value) = contextObjectMapper.set(value)

    /** 직렬화에 쓸 Jackson 매퍼. [ExtractionSettings.objectMapper] → 컨텍스트 매퍼 빈 → 클래스패스 기본 매퍼. */
    internal fun objectMapper(): Any =
        current.extraction.objectMapper?.objectMapper() ?: contextObjectMapper.get() ?: DefaultObjectMapper.instance

    /** 실제 스니펫 디렉터리. */
    internal fun snippetsDirectory(): File =
        File(current.output.snippetsDirectory ?: "${buildDirectory()}/generated-snippets")

    /** 실제 AsciiDoc 출력 디렉터리. */
    internal fun asciidocDirectory(): File =
        File(current.output.asciidocDirectory ?: "${buildDirectory()}/generated-asciidoc")

    /** Spring REST Docs 와 같은 규칙: 작업 디렉터리에 `pom.xml` 이 있으면 Maven(`target`), 아니면 Gradle(`build`). */
    internal fun buildDirectory(workingDirectory: File = File(".")): String =
        if (File(workingDirectory, "pom.xml").isFile) "target" else "build"
}
