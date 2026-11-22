package io.github.kardx.boot

/**
 * 시작 로그 문구의 언어. core 의 `DocsLanguage` 와 같은 값이다 (boot 는 core 에 의존하지 않는다).
 * `kardx.language` (yml) → Gradle 플러그인이 남긴 [ApiDocsLocations.DEFAULTS_RESOURCE] 의 `language` → 영어 순서로 정한다.
 */
internal enum class StartupLogLanguage {
    EN,
    KO,
    ;

    companion object {
        /** 지원하는 값 목록 (`en, ko`). 잘못된 값을 알리는 경고에 쓴다. */
        val SUPPORTED: String = entries.joinToString { it.name.lowercase() }

        /** `en`·`ko` (대소문자·앞뒤 공백 무시). 그 밖의 값이면 null. */
        fun parse(value: String): StartupLogLanguage? = entries.firstOrNull {
            it.name.equals(value.trim(), ignoreCase = true)
        }
    }
}
