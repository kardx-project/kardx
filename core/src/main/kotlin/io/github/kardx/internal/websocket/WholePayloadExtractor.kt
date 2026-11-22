package io.github.kardx.internal.websocket

import org.springframework.http.MediaType
import org.springframework.restdocs.payload.PayloadSubsectionExtractor

/** 페이로드 전체를 그대로 쓰는 subsection 추출기. 스니펫 이름을 `request-fields-<key>` 로 만들기 위해 쓴다. */
internal class WholePayloadExtractor(private val subsectionId: String) :
    PayloadSubsectionExtractor<WholePayloadExtractor> {
    override fun extractSubsection(payload: ByteArray, contentType: MediaType?): ByteArray = payload

    override fun getSubsectionId(): String = subsectionId

    override fun withSubsectionId(subsectionId: String): WholePayloadExtractor = WholePayloadExtractor(subsectionId)
}
