package io.github.kardx.websocket

import io.github.kardx.diagnostics.RestDocsErrorCode
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [StompTestClient.subscribe] 로 연 구독. 받은 프레임은 순서대로 쌓이고 [await] 가 하나씩 꺼낸다.
 *
 * ```kotlin
 * val subscription = stomp.subscribe("/topic/notices")
 * stomp.send("/app/notices.publish", mapOf("text" to "점검"))
 * val frame = subscription.await()
 * subscription.unsubscribe()
 * ```
 *
 * @property destination 구독한 목적지
 */
public class StompSubscription internal constructor(
    public val destination: String,
    private val frames: BlockingQueue<StompFrame>,
    private val defaultTimeout: Duration,
    private val failure: () -> String?,
    private val cancel: () -> Unit,
) {
    private val active = AtomicBoolean(true)

    /**
     * 다음 프레임을 [timeout] 까지 기다려 꺼낸다.
     *
     * ```kotlin
     * val frame = subscription.await(Duration.ofSeconds(2))
     * ```
     *
     * @throws IllegalStateException 시간 안에 프레임이 오지 않거나, 기다리는 동안 서버가 `ERROR` 프레임을 보내거나 연결이 끊기면
     */
    public fun await(timeout: Duration = defaultTimeout): StompFrame = poll(timeout) ?: error(
        RestDocsErrorCode.MESSAGE_TIMEOUT.message("No STOMP frame arrived on $destination within $timeout."),
    )

    /**
     * 구독을 끝낸다. 이미 끝났거나 연결이 끊겼으면 아무것도 하지 않는다.
     *
     * ```kotlin
     * subscription.unsubscribe()
     * ```
     */
    public fun unsubscribe() {
        if (active.compareAndSet(true, false)) {
            cancel()
        }
    }

    /**
     * 다음 프레임. [timeout] 안에 오지 않으면 null. 이미 받은 프레임은 먼저 돌려주고, 세션이 실패했으면 그 이유로 실패한다.
     * 실패는 대기열에 [WAKE] 를 넣어 기다리는 중에도 바로 알린다.
     */
    internal fun poll(timeout: Duration): StompFrame? {
        val frame = frames.poll()?.takeUnless { it === WAKE }
            ?: failure()?.let { error(it) }
            ?: frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS)

        if (frame === WAKE) {
            error(failure() ?: "STOMP session failed.")
        }

        return frame
    }

    internal companion object {
        /** 세션이 실패했을 때 기다리는 [poll] 을 깨우는 표시. 실제 프레임이 아니다. */
        val WAKE: StompFrame = StompFrame(emptyMap(), "")
    }
}
