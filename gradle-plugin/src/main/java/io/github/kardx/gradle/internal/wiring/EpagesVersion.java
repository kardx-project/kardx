package io.github.kardx.gradle.internal.wiring;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 적용된 epages 플러그인({@code com.epages.restdocs-api-spec})의 버전. epages jar 에는 버전 정보가 없어 플러그인 클래스가 로드된
 * jar 파일 이름({@code restdocs-api-spec-gradle-plugin-0.20.2.jar})에서 읽는다. jar 가 아니면(소스로 포함한 빌드 등) 모른다.
 */
final class EpagesVersion {
    /** 문서가 권하는 epages 버전 (Spring Boot 3 / 4). */
    private static final String FOR_BOOT_3 = "0.19.4";
    private static final String FOR_BOOT_4 = "0.20.2";
    private static final Pattern JAR = Pattern.compile("restdocs-api-spec-gradle-plugin-(\\d+\\.\\d+[^/\\\\]*)\\.jar$");

    private EpagesVersion() {
    }

    /** 플러그인 객체의 jar 에서 읽은 버전. 모르면 null. */
    static String of(Object plugin) {
        CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();

        if (location == null) {
            return null;
        }

        try {
            return parse(Path.of(location.toURI()).getFileName().toString());
        } catch (URISyntaxException | IllegalArgumentException e) {
            // 파일이 아닌 위치(jar 안의 jar 등)면 버전을 모르는 것으로 본다
            return null;
        }
    }

    /** jar 파일 이름에서 버전. 형식이 다르면 null. */
    static String parse(String fileName) {
        Matcher matcher = JAR.matcher(fileName);

        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Spring Boot 주 버전과 epages 버전 계열이 어긋나면 경고 문구 (Boot 3 → 0.19.x, Boot 4 → 0.20.x). 맞거나 모르면 null.
     */
    static String mismatch(int bootMajor, String version) {
        if (version == null) {
            return null;
        }

        String expected = switch (bootMajor) {
            case 3 -> "0.19.";
            case 4 -> "0.20.";
            default -> null;
        };

        if (expected == null || version.startsWith(expected)) {
            return null;
        }

        return "kardx: com.epages.restdocs-api-spec " + version + " does not match Spring Boot " + bootMajor
            + ". Use epages " + expected + "x with Spring Boot " + bootMajor
            + " (0.19.x for Spring Boot 3 / REST Docs 3, 0.20.x for Spring Boot 4 / REST Docs 4).";
    }

    /**
     * epages 가 없을 때 {@code apiDocs} 위치 출력의 OpenAPI 줄 ({@code off (apply id("…") version "0.20.2" to enable …)}).
     * 버전은 Spring Boot 주 버전을 따르고, 모르면 Boot 4 계열이다.
     */
    static String offHint(int bootMajor) {
        String version = bootMajor == 3 ? FOR_BOOT_3 : FOR_BOOT_4;

        return "off (apply id(\"com.epages.restdocs-api-spec\") version \"" + version
            + "\" to enable OpenAPI, coverage and the viewer)";
    }
}
