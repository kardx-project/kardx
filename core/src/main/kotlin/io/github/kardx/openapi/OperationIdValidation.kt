package io.github.kardx.openapi

/**
 * OpenAPI operationId 로 쓸 수 있는 값인지. 비어 있거나 공백 글자(띄어쓰기, 탭, 줄바꿈)가 있으면 안 된다.
 * 그 밖의 글자는 OpenAPI 스펙이 막지 않으므로 받는다 (코드 생성기에 맞추려면 영문·숫자를 권한다).
 * DSL(`openApi { operationId }`)과 [io.github.kardx.config.OpenApiSettings.operationIdResolver] 결과 검사에 쓴다.
 */
internal fun isValidOperationId(id: String): Boolean = id.isNotEmpty() && id.none(Char::isWhitespace)
