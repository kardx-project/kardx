package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.tooling.internal.openapi.ApiVersioningSettings;
import io.github.kardx.tooling.internal.openapi.OpenApiSecuritySettings;
import java.util.LinkedHashMap;
import org.gradle.api.Task;

/**
 * 스펙 후처리가 읽는 확장 값({@code openApiSecurity}, {@code apiVersioning})을 {@code openapi3} 의 입력으로 선언하고, 태스크
 * 액션에서 후처리 설정으로 바꾼다. {@code openapi3} 는 상태를 추적할 수 없어 늘 돈다({@link EpagesTaskOutputs}). 입력 선언은
 * 추적할 수 있게 되면 값이 바뀔 때 다시 돌게 하려는 것이다.
 */
final class SpecSettingsInputs {
    private SpecSettingsInputs() {
    }

    static void declare(Task task, KardxExtension.OpenApiSecurity security,
        KardxExtension.ApiVersioning versioning) {
        task.getInputs().property("restdocsOAuth2Flows", security.getOauth2().getFlows());
        task.getInputs().property("restdocsOAuth2Scopes", security.getOauth2().getScopes());
        task.getInputs()
            .property("restdocsOpenIdConnectUrl", security.getOpenIdConnectUrl())
            .optional(true);
        task.getInputs()
            .property("restdocsApiVersionStyle", versioning.getStyle())
            .optional(true);
        task.getInputs()
            .property("restdocsApiVersionName", versioning.getName())
            .optional(true);
        task.getInputs().property("restdocsApiVersionSplit", versioning.getSplitSpecs());
    }

    static OpenApiSecuritySettings security(KardxExtension.OpenApiSecurity security) {
        return new OpenApiSecuritySettings(new LinkedHashMap<>(security.getOauth2().getFlows().get()),
            new LinkedHashMap<>(security.getOauth2().getScopes().get()), security.getOpenIdConnectUrl().getOrNull());
    }

    static ApiVersioningSettings versioning(KardxExtension.ApiVersioning versioning) {
        return new ApiVersioningSettings(versioning.getStyle().getOrNull(), versioning.getName().getOrNull(),
            versioning.getSplitSpecs().get());
    }
}
