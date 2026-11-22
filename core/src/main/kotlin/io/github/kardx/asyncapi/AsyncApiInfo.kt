package io.github.kardx.asyncapi

import java.util.Objects

/**
 * AsyncAPI 문서 정보. [AsyncApiGenerator.generate] 에 넘기며, 명령행(`AsyncApiCli`)에서는 `--title`, `--version`,
 * `--description`, `--server`, `--security` 로 정한다 (yml 키는 없다).
 *
 * ```kotlin
 * val info = AsyncApiInfo(
 *     title = "Chat",
 *     version = "1.0.0",
 *     servers = listOf(AsyncApiServer("stomp", "localhost:8080", "stomp", "/ws")),
 *     securitySchemes = listOf(AsyncApiSecurityScheme.bearer("bearerAuth")),
 * )
 * ```
 *
 * @property title 문서 제목. `asyncapi.yaml` 의 `info.title` 이 되고, HTML 뷰어의 제목으로도 쓴다.
 *   명령행 기본값 `API`
 * @property version 문서 버전. `info.version` 이 된다. 명령행 기본값 `1.0.0`
 * @property description 문서 설명. 있으면 `info.description` 이 되고, null 이면 빠진다
 * @property servers 메시지가 오가는 서버. 문서 최상위 `servers` 에 [AsyncApiServer.name] 을 키로 들어간다.
 *   메시지의 프로토콜(STOMP, WebSocket, SSE)과 [AsyncApiServer.protocol] 이 맞는 서버에 채널이 연결된다.
 *   비어 있으면 `servers` 를 쓰지 않는다
 * @property securitySchemes 인증 방식. `components.securitySchemes` 에 들어가고, 모든 서버의 `security` 가 이것을
 *   가리킨다 (그중 하나로 인증한다). 비어 있으면 둘 다 쓰지 않는다
 * @throws IllegalArgumentException [securitySchemes] 에 이름이 같은 인증 방식이 있으면
 */
public class AsyncApiInfo(
    public val title: String,
    public val version: String,
    public val description: String? = null,
    public val servers: List<AsyncApiServer> = emptyList(),
    public val securitySchemes: List<AsyncApiSecurityScheme> = emptyList(),
) {
    init {
        val duplicates = securitySchemes.groupingBy { it.name }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        require(duplicates.isEmpty()) { "duplicate security scheme names: ${duplicates.joinToString()}" }
    }

    override fun equals(other: Any?): Boolean = other is AsyncApiInfo &&
        title == other.title &&
        version == other.version &&
        description == other.description &&
        servers == other.servers &&
        securitySchemes == other.securitySchemes

    override fun hashCode(): Int = Objects.hash(title, version, description, servers, securitySchemes)

    override fun toString(): String = "AsyncApiInfo(title=$title, version=$version, description=$description, " +
        "servers=$servers, securitySchemes=$securitySchemes)"
}

/**
 * AsyncAPI 서버. 문서 최상위 `servers.<name>` 항목 하나가 된다.
 *
 * @property name 서버 키. `servers` 의 키이자 채널이 `#/servers/<name>` 으로 가리키는 이름이다
 * @property host 호스트와 포트 (예: `localhost:8080`). `host` 로 들어간다
 * @property protocol `stomp`, `ws`, `wss`, `http`, `https` 등. 메시지 프로토콜과 이렇게 연결된다:
 *   STOMP → `stomp`/`stomps`, WebSocket → `ws`/`wss`, SSE → `http`/`https`
 * @property pathname 엔드포인트 경로 (예: STOMP 의 `/ws`). null 이면 `pathname` 을 쓰지 않는다
 * @property description 서버 설명. null 이면 `description` 을 쓰지 않는다
 */
public class AsyncApiServer(
    public val name: String,
    public val host: String,
    public val protocol: String,
    public val pathname: String? = null,
    public val description: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is AsyncApiServer &&
        name == other.name &&
        host == other.host &&
        protocol == other.protocol &&
        pathname == other.pathname &&
        description == other.description

    override fun hashCode(): Int = Objects.hash(name, host, protocol, pathname, description)

    override fun toString(): String =
        "AsyncApiServer(name=$name, host=$host, protocol=$protocol, pathname=$pathname, description=$description)"
}
