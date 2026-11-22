package com.example.restdocs.webflux.common

import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer

/**
 * 문서 디렉터리(`/docs/` 아래)의 YAML 을 `text/yaml` 로 내보낸다.
 *
 * WebFlux(Netty)는 `.yaml` 의 미디어 타입을 몰라 `application/octet-stream` 으로 보내므로 브라우저가
 * `/docs/openapi3.yaml`·`/docs/asyncapi.yaml` 을 내려받기로 연다. 서블릿 앱의 `server.mime-mappings` 는
 * WebFlux 에는 쓰이지 않으므로 리소스 핸들러에 직접 준다. 위치는 Gradle 플러그인 `bootJar.path`(`static/docs`)와 같다.
 */
@Configuration(proxyBeanMethods = false)
class DocsResourceConfig : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/docs/**")
            .addResourceLocations("classpath:/static/docs/")
            .setMediaTypes(mapOf("yaml" to YAML, "yml" to YAML))
    }

    private companion object {
        val YAML = MediaType("text", "yaml", Charsets.UTF_8)
    }
}
