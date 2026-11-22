package io.github.kardx.internal.asyncapi

import io.github.kardx.internal.schema.SchemaField

/**
 * 테스트가 남기는 AsyncAPI 메타데이터 한 건. 쓰는 쪽([AsyncApiMetadata.write])과
 * 읽는 쪽([io.github.kardx.asyncapi.AsyncApiDocumentBuilder])이 같은 타입을 쓴다.
 * 디스크 형식(`*.asyncapi.json` 의 키 이름)은 [AsyncApiRecordCodec] 이 정한다.
 */
internal sealed interface AsyncApiRecord {
    /** 메시지 프로토콜 (`STOMP`, `WebSocket`, `SSE`) */
    val protocol: String

    /** 목적지(destination), 연결 엔드포인트, 또는 SSE 경로 */
    val address: String
}

/** WebSocket / STOMP 메시지 한 건 (`kind: message`). */
internal data class MessageRecord(
    /** 스니펫 이름 (`message-01`) */
    val key: String?,
    /** 사용자가 준 AsyncAPI 메시지 이름. 없으면 방향과 목적지로 만든다 */
    val name: String?,
    override val protocol: String,
    /** [io.github.kardx.dsl.MessageDirection] 이름 (`SEND`, `SUBSCRIBE`, `CONNECT`) */
    val direction: String,
    override val address: String,
    val description: String?,
    val headers: List<HeaderRecord>,
    /** 실제 페이로드 (JSON 이면 예시로 파싱한다) */
    val payload: String?,
    val fields: List<SchemaField>,
) : AsyncApiRecord {
    /** 연결 헤더(STOMP CONNECT, WebSocket 핸드셰이크)인지. 메시지·채널을 만들지 않고 채널 바인딩 헤더가 된다 */
    val isConnect: Boolean get() = direction == CONNECT

    companion object {
        const val CONNECT: String = "CONNECT"
        const val SEND: String = "SEND"
    }
}

/** SSE 스트림 한 건 (`kind: event-stream`). */
internal data class EventStreamRecord(
    override val protocol: String,
    override val address: String,
    val requestHeaders: List<HeaderRecord>,
    val events: List<EventRecord>,
) : AsyncApiRecord

/** SSE 이벤트 하나. [payload] 는 스트림에서 그 이름의 첫 `data` 다. */
internal data class EventRecord(
    val name: String,
    val description: String?,
    val optional: Boolean,
    val payload: String?,
    val fields: List<SchemaField>,
)

/** 문서화한 헤더. [example] 은 마스킹을 거친 실제 값이다. */
internal data class HeaderRecord(
    val name: String,
    val description: String?,
    val optional: Boolean,
    val example: String?,
)

/** 메타데이터를 남긴 테스트. [AsyncApiMetadata.write] 가 채운다. */
internal data class AsyncApiOrigin(
    /** 테스트 클래스 `@DisplayName` (operation tag) */
    val tag: String?,
    /** 테스트 메서드 `@DisplayName` (operation summary) */
    val summary: String?,
    /** 스니펫 디렉터리 (`chat-test/send`) */
    val operation: String?,
)

/** 디스크의 메타데이터 파일 하나 = 출처 + 내용. */
internal data class AsyncApiEntry(val origin: AsyncApiOrigin, val record: AsyncApiRecord)
