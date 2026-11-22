package io.github.kardx.gradle.dsl;

import org.gradle.api.provider.Property;

/**
 * REST Docs HTML 변환({@code asciidoctor}) 설정. {@code apiDocs} 시간의 대부분이 이 변환이라, 로컬에서는 끄고 CI 에서만 켤 수 있게
 * 한다. 꺼도 OpenAPI·AsyncAPI·뷰어·Postman·{@code .http}·문서화 범위 보고서는 그대로 만들어지고, {@code apiDocs} 결과와 앱
 * 리소스({@code /docs})에는 HTML 이 빠진다 (지난 실행의 HTML 도 지운다). {@code ./gradlew asciidoctor} 를 직접 부르면 꺼져 있어도
 * 변환한다.
 *
 * <pre>{@code
 * kardx {
 *     html {
 *         enabled = false                                          // 언제나 끈다
 *         enabled = providers.environmentVariable("CI").isPresent  // CI 에서만 켠다
 *     }
 * }
 * }</pre>
 *
 * <p>빌드 스크립트를 바꾸지 않고 내 컴퓨터에서만 끄려면 {@code ~/.gradle/gradle.properties} 에
 * {@code kardx.html=false} 를 적거나 한 번만 {@code ./gradlew apiDocs -Pkardx.html=false}.
 */
public abstract class Html {
    /** 기본값을 정하는 Gradle 속성 이름. */
    public static final String PROPERTY = "kardx.html";

    /**
     * {@code apiDocs} 가 {@code asciidoctor} 를 실행하고 그 HTML 을 넣을지 여부. 기본값은 Gradle 속성
     * {@value #PROPERTY}({@code true}/{@code false}), 없으면 true. 빌드 스크립트에서 정한 값이 속성보다 앞선다.
     */
    public abstract Property<Boolean> getEnabled();
}
