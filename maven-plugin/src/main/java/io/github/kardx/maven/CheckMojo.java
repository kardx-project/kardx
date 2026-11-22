package io.github.kardx.maven;

import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.pipeline.ApiDocsPipeline;
import io.github.kardx.tooling.internal.pipeline.ConfigMaps;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * 만든 OpenAPI 스펙을 커밋해 둔 기준 스펙과 비교한다 (Gradle {@code apiDocsCheck}). 다르면 보고서를 쓰고 {@code failOn} 에 따라
 * 빌드를 실패시키거나 경고한다. 의도한 변경이면 {@code kardx:baseline} 으로 기준을 갱신한다.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractApiDocsMojo {
    /** 기준 스펙 파일. */
    @Parameter(property = "restdocs.baseline", defaultValue = "${project.basedir}/docs/openapi/openapi3.yaml")
    File baseline;

    /** {@code any}(차이가 있으면 실패), {@code breaking}(깨는 변경만), {@code none}(경고만). */
    @Parameter(property = "restdocs.failOn")
    String failOn;

    /** 기준 파일이 없으면 실패한다 ({@code failOn = none} 이면 경고만). */
    @Parameter
    Boolean requireBaseline;

    @Override
    void run(ToolLog log) {
        ApiDocsPipeline.check(config(), HINTS, log);
    }

    @Override
    void customize(Map<String, Object> settings) {
        ConfigMaps.put(settings, List.of("check", "baseline"), path(baseline));
        ConfigMaps.put(settings, List.of("check", "failOn"), failOn);
        ConfigMaps.put(settings, List.of("check", "requireBaseline"), requireBaseline);
        ConfigMaps.put(settings, List.of("check", "report"), path(new File(workDirectory, "openapi-changes.txt")));
    }
}
