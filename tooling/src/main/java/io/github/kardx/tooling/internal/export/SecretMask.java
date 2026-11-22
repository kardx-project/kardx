package io.github.kardx.tooling.internal.export;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 로그·오류 메시지에서 헤더 값을 가린다. 서버가 응답 본문에 받은 헤더를 그대로 돌려줘도 토큰이 빌드 로그에 남지 않게 한다.
 * 값 전체와, 값을 공백으로 나눈 긴 조각({@code Bearer <token>} 의 토큰)을 {@value #MASK} 로 바꾼다.
 */
public final class SecretMask {
    public static final String MASK = "****";
    /** 이보다 짧은 값은 가리지 않는다 (흔한 글자까지 지워 메시지를 읽을 수 없게 되므로). */
    private static final int MIN_SECRET = 4;
    /** {@code Bearer} 같은 스킴 이름은 남기고 토큰만 가리도록, 이보다 짧은 조각은 따로 가리지 않는다. */
    private static final int MIN_PART = 8;

    private final List<String> secrets;

    public SecretMask(Collection<String> values) {
        Set<String> secrets = new LinkedHashSet<>();

        for (String value : values) {
            if (value != null && value.length() >= MIN_SECRET) {
                secrets.add(value);
                addParts(value, secrets);
            }
        }

        // 긴 값부터 바꿔야 조각이 먼저 바뀌어 값 전체가 남는 일이 없다
        this.secrets = secrets.stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    }

    private static void addParts(String value, Set<String> secrets) {
        for (String part : value.trim().split("\\s+")) {
            if (part.length() >= MIN_PART) {
                secrets.add(part);
            }
        }
    }

    /** [text] 안의 헤더 값을 가린다. */
    public String apply(String text) {
        String masked = text;

        for (String secret : secrets) {
            masked = masked.replace(secret, MASK);
        }

        return masked;
    }
}
