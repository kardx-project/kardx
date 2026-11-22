package io.github.kardx.internal.websocket

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.restdocs.operation.OperationResponse
import org.springframework.restdocs.operation.OperationResponseFactory
import org.springframework.restdocs.operation.ResponseConverter

/** 메시지에는 응답이 없으므로 빈 `200` 응답을 만든다. 응답 스니펫은 만들지 않는다. */
internal object NoResponseConverter : ResponseConverter<Unit> {
    override fun convert(response: Unit): OperationResponse =
        OperationResponseFactory().create(HttpStatus.OK, HttpHeaders(), ByteArray(0))
}
