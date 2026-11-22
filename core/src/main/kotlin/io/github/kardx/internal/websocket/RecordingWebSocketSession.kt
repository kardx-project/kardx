package io.github.kardx.internal.websocket

import org.springframework.http.HttpHeaders
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketExtension
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.net.InetSocketAddress
import java.net.URI
import java.security.Principal
import java.util.concurrent.CopyOnWriteArrayList

/** 핸들러가 보내는 메시지를 기록하는 가짜 세션. */
internal class RecordingWebSocketSession(
    private val uri: URI,
    private val handshakeHeaders: HttpHeaders,
    private val attributes: MutableMap<String, Any>,
    private val principal: Principal?,
) : WebSocketSession {
    val sent: MutableList<WebSocketMessage<*>> = CopyOnWriteArrayList()

    @Volatile
    var closeStatus: CloseStatus? = null
        private set

    private var textLimit = Int.MAX_VALUE
    private var binaryLimit = Int.MAX_VALUE

    override fun getId(): String = "test-session"
    override fun getUri(): URI = uri
    override fun getHandshakeHeaders(): HttpHeaders = handshakeHeaders
    override fun getAttributes(): MutableMap<String, Any> = attributes
    override fun getPrincipal(): Principal? = principal
    override fun getLocalAddress(): InetSocketAddress = LOCAL_ADDRESS
    override fun getRemoteAddress(): InetSocketAddress = REMOTE_ADDRESS
    override fun getAcceptedProtocol(): String? = null
    override fun setTextMessageSizeLimit(messageSizeLimit: Int) {
        textLimit = messageSizeLimit
    }

    override fun getTextMessageSizeLimit(): Int = textLimit
    override fun setBinaryMessageSizeLimit(messageSizeLimit: Int) {
        binaryLimit = messageSizeLimit
    }

    override fun getBinaryMessageSizeLimit(): Int = binaryLimit
    override fun getExtensions(): List<WebSocketExtension> = emptyList()
    override fun sendMessage(message: WebSocketMessage<*>) {
        check(isOpen) { "Session is closed" }
        sent += message
    }

    override fun isOpen(): Boolean = closeStatus == null
    override fun close() = close(CloseStatus.NORMAL)
    override fun close(status: CloseStatus) {
        if (closeStatus == null) closeStatus = status
    }

    private companion object {
        /** 가짜 세션이 돌려주는 주소. 핸들러가 로그 등에 쓰는 경우를 위한 값일 뿐 실제로 열리지 않는다. */
        val LOCAL_ADDRESS = InetSocketAddress("localhost", 8080)
        val REMOTE_ADDRESS = InetSocketAddress("127.0.0.1", 50000)
    }
}
