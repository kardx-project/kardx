package io.github.kardx.internal.spring

import io.github.kardx.diagnostics.RestDocsErrorCode
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.PropertyResolver
import java.util.concurrent.ConcurrentHashMap

/**
 * 앱 설정(`application.yml`, 시스템 속성, 환경 변수)에서 `kardx.` 로 시작하는데 이 라이브러리가 모르는 속성을 찾아
 * 경고한다. 오타 난 키는 아무 효과 없이 무시되므로 조용히 기본값으로 문서가 만들어지는 것을 막는다.
 * 이름을 다 알 수 있는 속성 소스([EnumerablePropertySource])만 본다. 같은 이름은 JVM 에서 한 번만 경고한다.
 */
internal object UnknownPropertyWarnings {
    private val LOGGER: System.Logger = System.getLogger("io.github.kardx.config")
    private val warned: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 마지막으로 검사한 환경. 테스트마다 같은 컨텍스트로 불리므로 다시 훑지 않는다. */
    @Volatile
    private var lastChecked: PropertyResolver? = null

    fun check(environment: PropertyResolver) {
        if (environment !is ConfigurableEnvironment || environment === lastChecked) {
            return
        }

        lastChecked = environment

        unknownProperties(environment).forEach { (name, source) ->
            if (warned.add(name)) {
                LOGGER.log(System.Logger.Level.WARNING, message(name, source))
            }
        }
    }

    /** 모르는 속성 이름과 그 속성 소스 이름. */
    fun unknownProperties(environment: ConfigurableEnvironment): List<Pair<String, String>> =
        environment.propertySources
            .filterIsInstance<EnumerablePropertySource<*>>()
            .flatMap { source -> source.propertyNames.map { it to source.name } }
            .filter { (name, _) -> PropertyKeys.isOurs(name) && !PropertyKeys.isKnown(name) }
            .distinctBy { it.first }

    /** 경고 문구. 이름이 바뀐 옛 키면 새 키를, 아니면 가장 가까운 키를 알려 준다. */
    fun message(name: String, source: String): String {
        val hint = PropertyKeys.renamedTo(name)
            ?.let { " It was renamed to '$it'." }
            ?: PropertyKeys.suggestion(name)
                ?.let { " Did you mean '$it'?" }
                .orEmpty()

        return RestDocsErrorCode.UNKNOWN_PROPERTY.message(
            "unknown property '$name' in [$source] is ignored.$hint " +
                "Supported properties are listed in docs/configuration.md.",
        )
    }
}
