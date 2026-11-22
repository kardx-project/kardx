package io.github.kardx.tooling.internal.i18n;

import java.util.List;

/**
 * 생성 문서에 들어가는 플러그인 문구. 언어별 문구를 나란히 둔다. {@code ko} 문구는 언어 설정이 생기기 전 문구와 글자까지 같다.
 * {@code %s} 가 있는 문구는 {@link #format} 으로 채운다.
 */
public enum DocsText {
    /** index 끝 공통 오류 응답 절 제목. */
    COMMON_RESPONSES_TITLE("Common error responses", "공통 오류 응답"),
    /** 공통 오류 응답 절 첫 문장. */
    COMMON_RESPONSES_INTRO(
        "Every API can return the responses below, even when its own documentation does not list them.",
        "아래 응답은 API 마다 따로 적지 않았어도 모든 API 가 돌려줄 수 있습니다."),
    /** 인증 응답(401·403) 안내. {@code %s} 는 정한 상태 코드 ({@code `401`, `403`}). */
    COMMON_RESPONSES_AUTH(
        "Only APIs that require authentication return %s.",
        "%s 은 인증이 필요한 API 에만 해당합니다."),
    /** 응답 본문 스키마 안내. {@code %s} 는 스키마 이름. */
    COMMON_RESPONSES_SCHEMA("The response body schema is `%s`.", "응답 본문 스키마는 `%s` 입니다."),
    /** 공통 오류 응답 표 머리글 (AsciiDoc 표 행). */
    COMMON_RESPONSES_HEADER("|Status code |Description", "|상태 코드 |설명"),
    /** 집계 목록 페이지의 병합 스펙 링크 앞 글. */
    AGGREGATE_MERGED_SPEC("Merged OpenAPI: ", "전체 OpenAPI: "),
    /** 집계 목록 표의 프로젝트 열 머리글. */
    AGGREGATE_PROJECT("Project", "프로젝트"),
    /** OpenAPI 설명 끝 제약 문단의 머리. core 가 AsyncAPI 설명에 쓰는 머리와 같다. */
    CONSTRAINTS("Constraints: ", "제약: "),
    /** 응답 {@code Set-Cookie} 헤더 설명의 쿠키 목록 머리. */
    COOKIES("Cookies:", "쿠키:"),
    /** 선택 쿠키 표시. */
    OPTIONAL("(optional)", "(선택)"),
    /** OpenAPI {@code x-tagGroups} 에서 어느 그룹에도 넣지 않은 태그를 모으는 마지막 그룹 이름. */
    TAG_GROUP_OTHER("Other", "기타"),
    /** 뷰어 스크립트를 받지 못했을 때 페이지에 보이는 안내. {@code %s} 는 뷰어 이름 ({@code Redoc}). */
    VIEWER_OFFLINE(
        "Could not load the %s script (unpkg.com). Check your internet connection or open the OpenAPI spec file "
            + "in the same directory.",
        "%s 스크립트(unpkg.com)를 불러오지 못했습니다. 인터넷 연결을 확인하거나 같은 디렉터리의 OpenAPI 스펙 파일을 보세요.");

    private final String english;
    private final String korean;

    DocsText(String english, String korean) {
        this.english = english;
        this.korean = korean;
    }

    /** [language] 문구. */
    public String in(DocsLanguage language) {
        return language == DocsLanguage.KO ? korean : english;
    }

    /** [language] 문구의 {@code %s} 를 [args] 로 채운다. */
    public String format(DocsLanguage language, Object... args) {
        return String.format(in(language), args);
    }

    /** 모든 언어의 문구. 앞서 쓴(다른 언어일 수 있는) 문구를 알아볼 때 쓴다. */
    public List<String> variants() {
        return List.of(english, korean);
    }
}
