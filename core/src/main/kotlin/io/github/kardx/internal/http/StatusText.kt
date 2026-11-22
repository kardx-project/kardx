package io.github.kardx.internal.http

import org.springframework.http.HttpStatus

/** `404 Not Found`. 이유 문구를 모르는 코드는 숫자만. */
internal fun statusText(code: Int): String {
    val reason = HttpStatus.resolve(code)?.reasonPhrase

    return if (reason == null) "$code" else "$code $reason"
}
