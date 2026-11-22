package io.github.kardx.boot

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.core.io.Resource
import java.util.Collections
import java.util.Properties
import java.util.WeakHashMap

/**
 * 앱이 뜨면 API 문서 주소를 INFO 로그로 남긴다.
 *
 * ```
 * INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (REST Docs ) http://localhost:8080/docs/index.html
 * INFO ... d.r.kotlin.boot.ApiDocsStartupLogger : API docs (Swagger UI) http://localhost:8080/swagger-ui/index.html
 * ```
 *
 * `META-INF/spring.factories` 로 등록되므로 의존성만 있으면 동작한다. 웹 서버가 없는 앱(테스트의 MOCK 환경 등)에서는
 * 아무것도 남기지 않는다. `kardx.startup-log.enabled=false` 로 끈다.
 * 문서 경로는 `kardx.docs-path` (기본값: Gradle 플러그인이 남긴 값, 없으면 `docs`), 항목별 글자·파일·켜기 여부는
 * `kardx.startup-log.<rest-docs|swagger-ui|open-api-viewer|open-api|async-api>.*` 로 바꾼다.
 * 기본 문구는 영어이고 `kardx.language=ko` (또는 Gradle 플러그인의 같은 설정)면 한국어다.
 */
public class ApiDocsStartupLogger : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val context = event.applicationContext

        // 부모 컨텍스트(Spring Cloud bootstrap 등)가 있으면 같은 이벤트가 리스너 인스턴스마다 온다. 컨텍스트당 한 번만 남긴다.
        if (!LOGGED.add(context)) return

        val lines = ApiDocsLocations.lines(
            environment = context.environment,
            resourceExists = {
                context.getResource("classpath:$it")
                    .exists()
            },
            swaggerUiAvailable = SWAGGER_UI_CLASSES.any { isPresent(it, context.classLoader) },
            reactive = event.springApplication.webApplicationType == WebApplicationType.REACTIVE,
            defaults = pluginDefaults(context.getResource("classpath:${ApiDocsLocations.DEFAULTS_RESOURCE}")),
            warn = LOG::warn,
        )

        lines.forEach(LOG::info)
    }

    /** Gradle 플러그인이 남긴 문서 경로·파일 이름. 없으면(플러그인 없이 쓰거나 문서를 아직 만들지 않음) 빈 맵. */
    private fun pluginDefaults(resource: Resource): Map<String, String> {
        if (!resource.exists()) {
            return emptyMap()
        }

        val properties = Properties().apply { resource.inputStream.use(::load) }

        return properties.stringPropertyNames()
            .associateWith(properties::getProperty)
    }

    /** 클래스가 없거나 링크할 수 없으면(의존성 버전 불일치 등) 없는 것으로 본다. 예외는 판정 결과라 버린다. */
    private fun isPresent(className: String, loader: ClassLoader?): Boolean = try {
        Class.forName(className, false, loader ?: javaClass.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    } catch (_: LinkageError) {
        false
    }

    private companion object {
        val LOG: Log = LogFactory.getLog(ApiDocsStartupLogger::class.java)

        /** 이미 로그를 남긴 컨텍스트. 약한 참조라 컨텍스트가 닫히면 사라진다. */
        val LOGGED: MutableSet<Any> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

        /** Swagger UI 가 실제로 있는 starter(`-webmvc-ui`, `-webflux-ui`)에만 있는 클래스. `-api` starter 에는 없다. */
        val SWAGGER_UI_CLASSES =
            listOf("org.springdoc.webmvc.ui.SwaggerConfig", "org.springdoc.webflux.ui.SwaggerConfig")
    }
}
