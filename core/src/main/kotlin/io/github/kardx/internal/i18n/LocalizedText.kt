package io.github.kardx.internal.i18n

/**
 * 쓰는 시점의 언어로 고르는 [DocsText]. 캐시에 오래 남는 값(프로퍼티 정보 등)에 넣어 두고, 테스트마다 언어가 달라도
 * [resolve] 할 때의 설정을 따른다.
 */
internal data class LocalizedText(val text: DocsText, val arguments: List<String> = emptyList()) {
    /** 지금 설정의 언어로 만든 문구. */
    fun resolve(): String = text.text(*arguments.toTypedArray())
}
