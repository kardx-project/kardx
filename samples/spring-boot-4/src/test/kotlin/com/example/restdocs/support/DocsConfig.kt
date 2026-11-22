package com.example.restdocs.support

import com.example.restdocs.common.ErrorResponse
import io.github.kardx.config.RestDocsConfiguration

/**
 * 문서화 설정 중 코드로 정하는 것. [RestDocsTestSupport] 가 테스트마다 MockMvc 를 만들기 전에 부른다.
 *
 * 같은 설정을 `src/test/resources/application-docs.yml` 의 `kardx.*` 로도 할 수 있다.
 * 둘 다 있으면 코드가 우선한다. 이 샘플은 yml 과 코드를 나눠 쓰는 예다.
 *
 * | 어디서 | 무엇 |
 * |---|---|
 * | yml | 문서 머리말(`common-include-files`), 민감 헤더 마스킹, 응답 래퍼, Swagger 전역 헤더 |
 * | 코드 (여기) | 오류 응답 공통 스키마, 클래스·람다가 필요한 설정 |
 */
object DocsConfig {
    fun apply() {
        RestDocsConfiguration.configure {
            // 오류 응답(4xx·5xx)의 OpenAPI 스키마 이름을 모두 ErrorResponse 로. yml: open-api.error-schema
            openApi.errorSchema<ErrorResponse>()

            // 코드로만 할 수 있는 것: 람다·객체를 받는 설정
            masking.masker = null // 가리는 방식을 바꾸려면 { name, value -> ... }
            openApi.schemaNameResolver = null // 스키마 이름 규칙을 바꾸려면 { type -> "Dto" + SchemaNames.of(type) }
        }
    }
}
