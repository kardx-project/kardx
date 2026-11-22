package io.github.kardx.gradle.internal.wiring;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.openapi.OpenApiCommonResponses;
import io.github.kardx.tooling.internal.pages.CommonResponsesSection;
import java.io.File;
import java.util.Map;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code openApiCommonResponses} 를 REST Docs HTML 에 한 번 보여 준다: {@code apiDocsIndex} 가 만든 {@code index.adoc} 끝에
 * 공통 오류 응답 표({@link CommonResponsesSection})를 붙인다. index 는 플러그인이 이미 만드는 문서라 사용자의 문서·템플릿을
 * 고치지 않고 Asciidoctor 가 HTML 첫 페이지에 넣는다. 테스트 클래스별 문서에는 넣지 않는다.
 */
public final class CommonResponsesIndex {
    private CommonResponsesIndex() {
    }

    public static void configure(Project project, KardxExtension extension, TaskProvider<JavaExec> index) {
        Provider<RegularFile> output = IndexTasks.indexFile(project);
        MapProperty<Integer, String> responses = extension.getOpenApiCommonResponses();
        Property<String> schema = extension.getOpenApiCommonResponseSchema();
        // 언어 입력은 apiDocsIndex 에 이미 있다 (IndexTasks)
        Provider<String> language = DocsLanguageSetup.effective(extension);

        index.configure(task -> {
            task.getInputs().property("commonResponses", responses);
            task.getInputs()
                .property("commonResponseSchema", schema)
                .optional(true);
            // Gradle 은 Java 람다로 넘긴 태스크 액션의 구현을 추적하지 못한다(빌드 캐시·up-to-date). 그래서 익명 클래스로 쓴다
            task.doLast(new Action<>() {
                @Override
                public void execute(@Nonnull Task t) {
                    append(output.get().getAsFile(), responses.get(), schema.getOrNull(),
                        DocsLanguage.of(language.get()));
                }
            });
        });
    }

    private static void append(File index, Map<Integer, String> responses, String schema, DocsLanguage language) {
        if (responses.isEmpty() || !index.isFile()) {
            return;
        }

        String section = CommonResponsesSection.render(OpenApiCommonResponses.descriptions(responses), schema,
            language);

        TextFiles.write(index, TextFiles.read(index) + section);
    }
}
