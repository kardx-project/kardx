package com.example.restdocs.product

/** 예제용 상품 설명서. 실제 PDF 처럼 머리말 뒤에 텍스트가 아닌 바이트가 들어 있다. */
object ProductManual {
    /** PDF 머리말 다음 줄의 바이너리 표시 바이트 (PDF 명세가 권하는 128 이상의 바이트 네 개). */
    private val BINARY_MARKER = byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A)

    /** [id] 상품의 [lang] 설명서 PDF 바이트. */
    fun pdf(id: Long, lang: String): ByteArray = "%PDF-1.4\n".toByteArray() +
        BINARY_MARKER +
        "% product $id manual ($lang)\n%%EOF\n".toByteArray()
}
