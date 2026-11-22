package io.github.kardx.internal.http

import org.springframework.http.HttpHeaders

/**
 * 파일 본문(다운로드 응답, `application/octet-stream` 업로드 요청 …) 한 건을 문서에 보여 줄 정보.
 * 전처리([BinaryBodyPlaceholder]) 뒤의 본문과 헤더에서 읽으므로 크기는 자리 표시 안의 원래 크기다.
 *
 * @property mediaType `charset` 파라미터를 뺀 Content-Type (`application/pdf`). 헤더가 없으면 null
 * @property fileName `Content-Disposition` 의 파일 이름. 헤더가 없거나 이름이 없으면 null
 * @property size 원래 본문 크기 (바이트)
 */
internal class FileBody private constructor(val mediaType: String?, val fileName: String?, val size: Long) {
    companion object {
        private const val CHARSET = "charset="

        /** 글자가 아닌 본문인지. 비었거나 Content-Type 이 글자([TextualContent])면 false. */
        fun isBinary(content: ByteArray, headers: HttpHeaders): Boolean =
            content.isNotEmpty() && !TextualContent.isTextual(contentType(headers))

        fun of(content: ByteArray, headers: HttpHeaders): FileBody = FileBody(
            mediaType = contentType(headers)?.let(::withoutCharset),
            fileName = fileName(headers),
            size = BinaryBodyPlaceholder.originalSize(content) ?: content.size.toLong(),
        )

        /** `application/pdf;charset=UTF-8` → `application/pdf`. 다른 파라미터는 그대로 둔다. */
        private fun withoutCharset(contentType: String): String = contentType.split(';')
            .map(String::trim)
            .filterNot { it.startsWith(CHARSET, ignoreCase = true) }
            .joinToString(";")

        private fun contentType(headers: HttpHeaders): String? =
            HttpHeadersCompat.values(headers, HttpHeaders.CONTENT_TYPE)
                .firstOrNull()

        /** 형식이 잘못된 `Content-Disposition` 은 이름이 없는 것으로 본다 (문서화가 헤더 형식으로 실패하지 않게). */
        private fun fileName(headers: HttpHeaders): String? = runCatching { headers.contentDisposition.filename }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
