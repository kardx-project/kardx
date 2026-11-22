package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.internal.PluginConstants;
import org.gradle.api.Project;
import org.gradle.api.Task;

/**
 * epages {@code openapi3 { }} 의 기본값을 이 라이브러리에 맞춘다. 사용자가 적은 값은 덮어쓰지 않는다.
 * <ul>
 *   <li>{@code format = "yaml"}: boot 모듈·문서가 기대하는 {@code openapi3.yaml} (epages 기본값은 json)</li>
 *   <li>{@code title}: 프로젝트 이름 (epages 기본값 {@code API documentation})</li>
 *   <li>{@code version}: 프로젝트 버전. {@code unspecified} 면 epages 기본값({@code 1.0.0})을 둔다</li>
 *   <li>server {@code "/"}: Swagger UI 의 Try it out 이 문서를 연 서버로 요청한다 (epages 기본값 {@code http://localhost})</li>
 * </ul>
 *
 * <p>epages 확장은 Gradle {@code Property} 가 아닌 일반 필드라 convention 을 쓸 수 없다. 그래서 epages 가 적용되자마자(빌드
 * 스크립트 본문이 실행되기 전) 값을 넣고, 사용자가 {@code openapi3 { }} 에서 쓴 값이 그 뒤에 덮어쓴다. 프로젝트 버전은 빌드
 * 스크립트 본문에서 정하는 경우가 많으므로, 넣은 값 객체를 기억해 두었다가 afterEvaluate 에서 그 객체가 그대로면(사용자가 다른 값을
 * 넣지 않았으면, 같은 글자를 넣었더라도 다른 객체다) 그때의 프로젝트 버전으로 바꾼다.
 */
final class OpenApiDefaults {
    static final String FORMAT = "yaml";
    static final String SERVER = "/";

    private static final Class<?>[] STRING = {String.class};
    private static final String UNSPECIFIED = "unspecified";

    private OpenApiDefaults() {
    }

    static void apply(Project project) {
        Object openapi3 = project.getExtensions().findByName(PluginConstants.OPENAPI3_TASK);

        if (openapi3 == null) {
            return;
        }

        Reflection.invokeIfPresent(openapi3, "setFormat", STRING, FORMAT);
        Reflection.invokeIfPresent(openapi3, "setTitle", STRING, project.getName());
        Reflection.invokeIfPresent(openapi3, "setServer", STRING, SERVER);

        Object current = Reflection.invokeIfPresent(openapi3, "getVersion");

        if (!(current instanceof String epagesVersion)) {
            return;
        }

        // 사용자가 값을 넣었는지 알아보려고 같은 글자의 새 객체를 넣는다 (char 배열에서 만든 문자열은 늘 새 객체다)
        String placeholder = String.valueOf(projectVersion(project, epagesVersion).toCharArray());

        Reflection.invoke(openapi3, "setVersion", STRING, placeholder);
        project.afterEvaluate(p -> {
            String version = projectVersion(p, null);

            if (version == null || !isSameObject(Reflection.invokeIfPresent(openapi3, "getVersion"), placeholder)) {
                return;
            }

            Reflection.invoke(openapi3, "setVersion", STRING, version);

            // epages 가 먼저 적용됐으면 그 afterEvaluate 가 이미 확장 값을 태스크에 옮겼다
            Task task = p.getTasks().findByName(PluginConstants.OPENAPI3_TASK);

            if (task != null) {
                Reflection.invokeIfPresent(task, "setApiVersion", STRING, version);
            }
        });
    }

    /** 프로젝트 버전. 정하지 않았으면 {@code fallback}. */
    private static String projectVersion(Project project, String fallback) {
        String version = String.valueOf(project.getVersion());

        return version.isBlank() || UNSPECIFIED.equals(version) ? fallback : version;
    }

    /** 같은 객체인가 (값이 아니라 객체를 비교한다). */
    private static boolean isSameObject(Object a, Object b) {
        return a == b;
    }
}
