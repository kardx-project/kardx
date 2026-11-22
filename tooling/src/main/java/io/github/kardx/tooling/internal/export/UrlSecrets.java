package io.github.kardx.tooling.internal.export;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 업로드 주소에 든 비밀값: 사용자 정보({@code user:password@})와 쿼리 파라미터 값({@code ?token=…}).
 * 주소를 로그·오류 메시지에 남길 때 {@link SecretMask} 로 가린다. 주소 문자열에 쓰인 형태(인코딩된 값)와 풀어 쓴 형태를 모두 넣는다.
 */
public final class UrlSecrets {
    private UrlSecrets() {
    }

    public static List<String> of(URI uri) {
        List<String> secrets = new ArrayList<>();

        if (uri.getRawUserInfo() != null) {
            addEncodedAndDecoded(uri.getRawUserInfo(), secrets);
            addPassword(uri.getRawUserInfo(), secrets);
        }

        if (uri.getRawQuery() != null) {
            for (String parameter : uri.getRawQuery().split("&")) {
                int equals = parameter.indexOf('=');

                if (equals >= 0) {
                    addEncodedAndDecoded(parameter.substring(equals + 1), secrets);
                }
            }
        }

        return secrets;
    }

    private static void addPassword(String userInfo, List<String> secrets) {
        int colon = userInfo.indexOf(':');

        if (colon >= 0) {
            addEncodedAndDecoded(userInfo.substring(colon + 1), secrets);
        }
    }

    private static void addEncodedAndDecoded(String raw, List<String> secrets) {
        secrets.add(raw);

        try {
            secrets.add(URLDecoder.decode(raw, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            // 잘못된 % 인코딩이면 쓰인 그대로만 가린다
        }
    }
}
