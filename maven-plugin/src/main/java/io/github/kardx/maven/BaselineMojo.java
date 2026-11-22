package io.github.kardx.maven;

import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.pipeline.ApiDocsPipeline;
import io.github.kardx.tooling.internal.pipeline.ConfigMaps;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** 만든 OpenAPI 스펙을 기준 스펙으로 복사한다 (Gradle {@code apiDocsBaseline}). 단계에 묶지 않고 직접 부른다. */
@Mojo(name = "baseline", threadSafe = true)
public class BaselineMojo extends AbstractApiDocsMojo {
    /** 기준 스펙 파일. */
    @Parameter(property = "restdocs.baseline", defaultValue = "${project.basedir}/docs/openapi/openapi3.yaml")
    File baseline;

    @Override
    void run(ToolLog log) {
        ApiDocsPipeline.baseline(config(), log);
    }

    @Override
    void customize(Map<String, Object> settings) {
        ConfigMaps.put(settings, List.of("check", "baseline"), path(baseline));
    }
}
