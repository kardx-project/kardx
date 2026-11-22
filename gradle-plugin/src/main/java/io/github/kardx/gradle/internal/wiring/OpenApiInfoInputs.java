package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.OpenApiInfo;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.openapi.OpenApiInfoSettings;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.PathSensitivity;

/**
 * {@code openApiInfo { }} 값을 {@code openapi3} 의 입력으로 선언하고({@link SpecSettingsInputs} 와 같은 이유), 태스크 액션에서
 * 후처리 설정({@link OpenApiInfoSettings})으로 바꾼다. 설명 파일은 태스크 액션에서 읽는다.
 */
final class OpenApiInfoInputs {
    private static final String PREFIX = "restdocsOpenApiInfo";
    private static final String NAME = "name";
    private static final String URL = "url";
    private static final String DESCRIPTION = "description";

    private OpenApiInfoInputs() {
    }

    static void declare(Task task, OpenApiInfo info) {
        optional(task, "Description", info.getDescription());
        optional(task, "TermsOfService", info.getTermsOfService());
        optional(task, "ContactName", info.getContact().getName());
        optional(task, "ContactUrl", info.getContact().getUrl());
        optional(task, "ContactEmail", info.getContact().getEmail());
        optional(task, "LicenseName", info.getLicense().getName());
        optional(task, "LicenseUrl", info.getLicense().getUrl());
        optional(task, "ExternalDocsUrl", info.getExternalDocs().getUrl());
        optional(task, "ExternalDocsDescription", info.getExternalDocs().getDescription());
        task.getInputs().property(PREFIX + "TagOrder", info.getTagOrder());
        task.getInputs().property(PREFIX + "TagDescriptions", info.getTagDescriptions());
        task.getInputs().property(PREFIX + "TagExternalDocs", info.getTagExternalDocs());
        task.getInputs().property(PREFIX + "TagGroups", info.getTagGroups());
        task.getInputs()
            .files(info.getDescriptionFile())
            .withPropertyName(PREFIX + "DescriptionFile")
            .withPathSensitivity(PathSensitivity.NONE)
            .optional();
    }

    static OpenApiInfoSettings settings(OpenApiInfo info) {
        Map<String, String> contact = new LinkedHashMap<>();
        Map<String, String> license = new LinkedHashMap<>();
        Map<String, String> externalDocs = new LinkedHashMap<>();
        Map<String, List<String>> groups = new LinkedHashMap<>();

        contact.put(NAME, info.getContact().getName().getOrNull());
        contact.put(URL, info.getContact().getUrl().getOrNull());
        contact.put("email", info.getContact().getEmail().getOrNull());
        license.put(NAME, info.getLicense().getName().getOrNull());
        license.put(URL, info.getLicense().getUrl().getOrNull());
        externalDocs.put(URL, info.getExternalDocs().getUrl().getOrNull());
        externalDocs.put(DESCRIPTION, info.getExternalDocs().getDescription().getOrNull());
        info.getTagGroups()
            .get()
            .forEach((name, tags) -> groups.put(name, new ArrayList<>(tags)));

        return new OpenApiInfoSettings(description(info), info.getTermsOfService().getOrNull(), contact, license,
            externalDocs, new ArrayList<>(info.getTagOrder().get()),
            new LinkedHashMap<>(info.getTagDescriptions().get()), new LinkedHashMap<>(info.getTagExternalDocs().get()),
            groups);
    }

    /** {@code description} 또는 {@code descriptionFile} 의 내용 (끝 공백 제외). 둘 다 정하면 어느 쪽인지 모르므로 실패한다. */
    static String description(OpenApiInfo info) {
        if (!info.getDescriptionFile().isPresent()) {
            return info.getDescription().getOrNull();
        }

        File file = info.getDescriptionFile().get().getAsFile();

        if (info.getDescription().isPresent()) {
            throw new GradleException("Set either kardx.openApiInfo.description or descriptionFile, not both "
                + "(descriptionFile = " + file + ")");
        }

        if (!file.isFile()) {
            throw new GradleException("kardx.openApiInfo.descriptionFile does not exist: " + file);
        }

        return TextFiles.read(file).stripTrailing();
    }

    private static void optional(Task task, String name, Property<String> value) {
        task.getInputs()
            .property(PREFIX + name, value)
            .optional(true);
    }
}
