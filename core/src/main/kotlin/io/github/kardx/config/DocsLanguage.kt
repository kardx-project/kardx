package io.github.kardx.config

/**
 * 라이브러리가 생성 문서·스펙·뷰어에 넣는 문구의 언어. [RestDocsSettings.language], yml `kardx.language`
 * (`en`, `ko`, 대소문자 무시).
 *
 * 문서 목록 제목·링크 글자, 스키마 설명 접두사(`Constraints: `), 자동 설명(Spring Data 페이지·`ProblemDetail`, 타입 구분자),
 * AsyncAPI 채널 설명과 뷰어 화면이 이 언어를 따른다. 로그·경고·예외 메시지와 CLI 출력은 언제나 영어다.
 */
public enum class DocsLanguage {
    /** 영어 (기본값). yml `en` */
    EN,

    /** 한국어. yml `ko` */
    KO,
}
