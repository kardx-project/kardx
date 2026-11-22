package io.github.kardx.tooling.internal.coverage;

import java.util.regex.Pattern;

/** 경로 비교: 경로 변수 이름을 무시한 정규형과 제외 패턴(Ant 형식) 일치. */
final class PathPatterns {
    /** 경로 변수 ({@code {id}}, {@code {id:\d+}}, {@code {*rest}}). 정규식 안의 중괄호 한 단계까지 안다. */
    private static final Pattern VARIABLE = Pattern.compile("\\{(?:[^{}]|\\{[^{}]*})*}");
    private static final String DOUBLE_STAR_SEGMENT = "/**";

    private PathPatterns() {
    }

    /** 변수 이름·정규식·쿼리·끝의 {@code /} 를 뺀 경로 ({@code /users/{userId}/} → {@code /users/{}}). */
    static String normalize(String path) {
        int query = path.indexOf('?');
        String normalized = VARIABLE.matcher(query < 0 ? path : path.substring(0, query)).replaceAll("{}");

        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    /** 경로 템플릿과 맞는 실제 경로의 정규식. 변수 자리는 {@code /} 없는 한 칸이다 ({@code /users/{id}} ↔ {@code /users/7}). */
    static Pattern template(String path) {
        String[] parts = normalize(path).split("\\{}", -1);
        StringBuilder regex = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            regex.append(Pattern.quote(parts[i]));

            if (i < parts.length - 1) {
                regex.append("[^/]+");
            }
        }

        return Pattern.compile(regex + "/?");
    }

    /**
     * Ant 패턴을 {@link #normalize} 한 경로와 맞춰 볼 정규식으로 바꾼다: {@code **} 는 여러 칸, {@code *} 는 한 칸 안의
     * 글자들, {@code ?} 는 글자 하나. {@code /actuator/**} 는 {@code /actuator} 자신도 맞는다. 경로 변수
     * ({@code {id}}, {@code {id:\d+}})는 엔드포인트처럼 이름을 무시하고 변수 자리와 맞는다.
     */
    static Pattern compile(String antPattern) {
        StringBuilder regex = new StringBuilder();
        String withoutNames = VARIABLE.matcher(antPattern).replaceAll("{}");
        String pattern = withoutNames.startsWith("/") ? withoutNames : "/" + withoutNames;
        int i = 0;

        while (i < pattern.length()) {
            if (pattern.startsWith(DOUBLE_STAR_SEGMENT, i)) {
                regex.append("(/.*)?");
                i += DOUBLE_STAR_SEGMENT.length();
            } else if (pattern.startsWith("**", i)) {
                regex.append(".*");
                i += 2;
            } else {
                regex.append(single(pattern.charAt(i)));
                i++;
            }
        }

        return Pattern.compile(regex.toString());
    }

    private static String single(char c) {
        if (c == '*') {
            return "[^/]*";
        }

        return c == '?' ? "[^/]" : Pattern.quote(String.valueOf(c));
    }
}
