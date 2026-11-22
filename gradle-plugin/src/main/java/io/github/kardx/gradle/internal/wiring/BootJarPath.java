package io.github.kardx.gradle.internal.wiring;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.gradle.api.GradleException;

/**
 * {@code kardx.bootJar.path} 확인. 문서는 앱 리소스({@code build/resources/main}) 안의 이 디렉터리로 들어가므로, 비었거나
 * 리소스 밖을 가리키거나 Spring 정적 위치 자체({@code static} 등)면 앱 파일과 섞이므로 거부한다.
 */
final class BootJarPath {
    static final String PROPERTY = "kardx.bootJar.path";

    /** Spring Boot 정적 위치와 그 상위. 문서를 여기에 바로 넣으면 앱의 {@code index.html} 등과 섞인다. */
    private static final Set<String> APP_ROOTS =
        Set.of("static", "public", "resources", "META-INF/resources", "META-INF");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

    private BootJarPath() {
    }

    /**
     * {@code \} 를 {@code /} 로, {@code .}·빈 마디와 끝 {@code /} 를 뺀 경로 ({@code ./static//docs/} → {@code static/docs}).
     *
     * @throws GradleException 비었거나, 절대 경로·드라이브·{@code ..} 를 쓰거나, Spring 정적 위치 자체이면
     */
    static String of(String original) {
        String path = original == null ? "" : original.replace('\\', '/');

        if (path.startsWith("/") || DRIVE.matcher(path).matches()) {
            throw invalid(original);
        }

        List<String> segments = new ArrayList<>();

        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                throw invalid(original);
            }

            if (!segment.isBlank() && !segment.equals(".")) {
                segments.add(segment);
            }
        }

        String normalized = String.join("/", segments);

        if (normalized.isEmpty()) {
            throw invalid(original);
        }

        if (APP_ROOTS.contains(normalized)) {
            throw new GradleException(PROPERTY + " '" + original + "' is an application resource root (static, public, "
                + "resources, META-INF/resources). The docs would mix with the app's own files there; use a sub "
                + "directory such as 'static/docs'.");
        }

        return normalized;
    }

    private static GradleException invalid(String original) {
        return new GradleException(PROPERTY + " must be a directory inside the app's resources such as 'static/docs' "
            + "or 'public/api-docs', got '" + original + "'");
    }
}
