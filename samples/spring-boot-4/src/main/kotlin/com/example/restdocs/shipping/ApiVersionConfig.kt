package com.example.restdocs.shipping

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Spring Framework 7 API 버전 설정. 클라이언트는 `X-API-Version` 헤더로 버전을 보낸다.
 *
 * 버전을 정한 매핑은 [ShippingFeeController] 뿐이다. 다른 API 는 버전 없이 그대로 부르도록 버전을 필수로 두지 않는다.
 * 문서 쪽 설정은 build 파일의 `kardx { apiVersioning { header("X-API-Version") } }` 이다.
 */
@Configuration
class ApiVersionConfig : WebMvcConfigurer {
    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer.useRequestHeader(HEADER)
            .setVersionRequired(false)
    }

    companion object {
        /** API 버전을 보내는 요청 헤더. */
        const val HEADER = "X-API-Version"
    }
}
