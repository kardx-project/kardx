package io.github.kardx.internal.websocket

import java.net.URI

/**
 * 문서용 WebSocket URI. 호스트는 의미 없는 `localhost` 고정이고 [path] 만 남긴다.
 * `URI` 의 path 는 `/` 로 시작해야 하므로 `chat.send`, `queue/x` 같은 브로커식 목적지도 앞에 `/` 를 붙인다.
 */
internal fun webSocketUri(path: String): URI = URI("ws", null, "localhost", -1, "/" + path.trimStart('/'), null, null)
