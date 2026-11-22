package io.github.kardx.tooling.internal.i18n;

import io.github.kardx.tooling.ToolingException;
import java.util.Arrays;
import java.util.Locale;

/**
 * 생성 문서(index, OpenAPI 설명, 뷰어·집계 페이지)에 쓰는 언어. 플러그인 확장 {@code kardx.language} 로 고른다.
 * Gradle 로그·경고·오류와 {@code apiDocsCheck} 보고서는 언어와 상관없이 영어다.
 */
public enum DocsLanguage {
    EN("en"),
    KO("ko");

    /** 정하지 않았을 때의 언어. */
    public static final DocsLanguage DEFAULT = EN;

    private final String code;

    DocsLanguage(String code) {
        this.code = code;
    }

    /** 소문자 언어 코드 ({@code en}, {@code ko}). 시스템 속성·{@code docs-locations.properties} 에 이 값을 넘긴다. */
    public String code() {
        return code;
    }

    /**
     * 확장에 적은 값. 대소문자와 앞뒤 공백은 가리지 않는다.
     *
     * @throws ToolingException 지원하지 않는 값이면
     */
    public static DocsLanguage of(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);

        return Arrays.stream(values())
            .filter(language -> language.code.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new ToolingException("kardx.language must be "
                + String.join(" or ", Arrays.stream(values()).map(DocsLanguage::code).toList()) + ", got '" + value
                + "'"));
    }
}
