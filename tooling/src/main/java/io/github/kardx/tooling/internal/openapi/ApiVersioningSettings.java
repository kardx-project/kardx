package io.github.kardx.tooling.internal.openapi;

/**
 * 플러그인 확장 {@code apiVersioning { }} 의 값 ({@link OpenApiApiVersions}, {@link OpenApiVersionSplit}).
 *
 * @param style {@code header}, {@code query}, {@code mediaType}, {@code path}. 정하지 않았으면 null
 * @param name 헤더·쿼리·미디어 타입 파라미터 이름. 없으면 null
 * @param splitSpecs 같은 operation 을 여러 버전으로 문서화했을 때 버전마다 스펙을 따로 만들지 여부
 */
public record ApiVersioningSettings(String style, String name, boolean splitSpecs) {
    /** 헤더 방식. */
    static final String HEADER = "header";
    /** 쿼리 파라미터 방식. */
    static final String QUERY = "query";

    /** 아무것도 정하지 않은 값. */
    public static final ApiVersioningSettings NONE = new ApiVersioningSettings(null, null, false);

    /** 버전을 받는 파라미터의 {@code in} ({@code header}, {@code query}). 그 밖의 방식이면 null. */
    String parameterLocation() {
        if (name == null) {
            return null;
        }

        return HEADER.equals(style) || QUERY.equals(style) ? style : null;
    }
}
