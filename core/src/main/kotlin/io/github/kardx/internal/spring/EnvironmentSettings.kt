package io.github.kardx.internal.spring

import io.github.kardx.config.RestDocsConfiguration
import org.springframework.core.env.PropertyResolver

/** 앱 설정의 `kardx.*` 속성을 반영하고, 모르는 속성(오타 등)이 있으면 경고한다. */
internal fun applyEnvironmentSettings(environment: PropertyResolver) {
    RestDocsConfiguration.applyEnvironment(environment)
    UnknownPropertyWarnings.check(environment)
}
