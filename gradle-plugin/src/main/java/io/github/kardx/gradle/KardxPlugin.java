package io.github.kardx.gradle;

import io.github.kardx.gradle.dsl.KardxExtension;
import io.github.kardx.gradle.internal.PluginConstants;
import io.github.kardx.gradle.internal.wiring.ApiDocsAggregation;
import io.github.kardx.gradle.internal.wiring.ApiDocsTasks;
import io.github.kardx.gradle.internal.wiring.AsciidoctorIntegration;
import io.github.kardx.gradle.internal.wiring.AsyncApiTasks;
import io.github.kardx.gradle.internal.wiring.CommonResponsesIndex;
import io.github.kardx.gradle.internal.wiring.ExportTasks;
import io.github.kardx.gradle.internal.wiring.ExtensionDefaults;
import io.github.kardx.gradle.internal.wiring.GenerateOnRun;
import io.github.kardx.gradle.internal.wiring.GradleVersionCheck;
import io.github.kardx.gradle.internal.wiring.IndexTasks;
import io.github.kardx.gradle.internal.wiring.LibraryDependencies;
import io.github.kardx.gradle.internal.wiring.OpenApiIntegration;
import io.github.kardx.gradle.internal.wiring.PluginServices;
import io.github.kardx.gradle.internal.wiring.SourceDocsTasks;
import io.github.kardx.gradle.internal.wiring.SpringBootIntegration;
import io.github.kardx.gradle.internal.wiring.TestTaskSetup;
import io.github.kardx.gradle.internal.wiring.WebFluxTestClasspath;
import io.github.kardx.gradle.internal.wiring.WireMockStubs;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * kardx 문서 파이프라인을 구성하는 Gradle 플러그인 ({@code id("io.github.kardx-project")}).
 *
 * <ul>
 *   <li>Gradle {@value GradleVersionCheck#MINIMUM_VERSION} 미만이면 바로 실패한다 ({@link GradleVersionCheck})</li>
 *   <li>라이브러리({@code annotations}, {@code core} 또는 Kotest 확장)와 테스트 클라이언트(REST Docs MockMvc·WebTestClient, epages)
 *       의존성을 넣는다 ({@link LibraryDependencies}, {@code addDependencies = false} 로 끈다)</li>
 *   <li>test: 스니펫·AsciiDoc 경로를 시스템 속성으로 넘기고, 실행 전에 지난 결과를 지운다 ({@link TestTaskSetup})</li>
 *   <li>{@code apiDocsSourceDocs}: (실험 기능, 기본 꺼짐) main 소스(와 프로젝트 의존성)의 KDoc·Javadoc 을 설명 색인으로
 *       모아 test 에 넘긴다 ({@link SourceDocsTasks})</li>
 *   <li>{@code apiDocsIndex}: 문서를 만든 테스트 클래스의 링크로 index 를 만든다. 설정·템플릿 없이도 된다 ({@link IndexTasks})</li>
 *   <li>{@code apiDocsAsciidocSources}: 직접 쓴 문서와 생성된 문서를 Asciidoctor 입력으로 모은다 ({@link IndexTasks})</li>
 *   <li>{@code asciidoctor} (org.asciidoctor.jvm.convert 가 있으면): 입력, 스니펫 경로, REST Docs 확장을 설정한다
 *       ({@link AsciidoctorIntegration})</li>
 *   <li>{@code apiDocsOpenApiSnippets}, {@code openapi3} (com.epages.restdocs-api-spec 가 있으면):
 *       {@code openapi3 { }} 기본값을 맞추고(yaml, 프로젝트 이름·버전, server "/"), 스니펫 복사본을 읽게 하고, 전역 헤더를
 *       apiKey 보안 스키마로 바꾸고, 선언한 공통 응답을 더한다 (index 문서에도 표로 넣는다, {@link CommonResponsesIndex})
 *       ({@link OpenApiIntegration})</li>
 *   <li>{@code apiDocsAsyncApi}: AsyncAPI 문서와 뷰어를 만든다 ({@link AsyncApiTasks})</li>
 *   <li>{@code apiDocs}, {@code apiDocsAppDocs}: HTML, OpenAPI, AsyncAPI 를 한 디렉터리로 모으고 위치를 출력한다
 *       ({@link ApiDocsTasks})</li>
 *   <li>{@code bootJar}, {@code developmentOnly} (Spring Boot 가 있으면): 위 결과를 실행 jar 에 넣고, 앱 시작 시 문서
 *       주소를 로그로 남기는 모듈을 넣는다 ({@link SpringBootIntegration}). 문서 경로·파일 이름을 그 모듈이 읽을 파일로 남기고,
 *       민감 헤더를 가리지 않은 문서가 들어가면 경고한다</li>
 *   <li>IDE 실행·{@code bootRun} 전에 문서를 만든다 ({@link GenerateOnRun})</li>
 *   <li>{@code apiDocsCheck}·{@code apiDocsBaseline}, {@code apiDocsViewer}, {@code apiDocsCoverage},
 *       {@code apiDocsHttpClient}, postman 연결 (epages 가 있으면,
 *       {@link OpenApiIntegration})</li>
 *   <li>{@code apiDocsAggregate}: 여러 프로젝트의 문서를 모은다 ({@link ApiDocsAggregation}). Java 플러그인이 없는
 *       루트 프로젝트에서도 동작한다</li>
 *   <li>{@code apiDocsStubsJar}: 문서화 테스트가 만든 WireMock 매핑을 묶는다 ({@link WireMockStubs})</li>
 *   <li>{@code apiDocsExport}, {@code apiDocsUpload}, Maven 배포물에 스펙 붙이기: 직접 실행할 때만 스펙을 밖으로 내보낸다
 *       ({@link ExportTasks})</li>
 *   <li>WebFlux 앱이면 테스트 런타임 클래스패스에서 {@code spring-boot-starter-web} 을 뺀다
 *       ({@link WebFluxTestClasspath})</li>
 * </ul>
 *
 * 다른 플러그인은 적용하지 않고, 적용돼 있을 때만 연결한다. 플러그인 순서와 상관없이 동작한다.
 * 태스크 액션은 {@link Project} 를 참조하지 않으므로 Gradle configuration cache 와 함께 쓸 수 있다.
 */
public class KardxPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        GradleVersionCheck.requireSupported();

        KardxExtension extension = project.getExtensions()
            .create(PluginConstants.EXTENSION_NAME, KardxExtension.class);

        ExtensionDefaults.apply(project, extension);
        ApiDocsAggregation.configureAggregator(project, extension);
        project.getPlugins().withType(JavaPlugin.class, java -> configure(project, extension));
    }

    private static void configure(Project project, KardxExtension extension) {
        SourceSet testSourceSet = project.getExtensions()
            .getByType(SourceSetContainer.class)
            .getByName(SourceSet.TEST_SOURCE_SET_NAME);
        FileCollection testClasspath = testSourceSet.getRuntimeClasspath();
        // 앱·테스트 클래스 출력을 뺀 테스트 런타임 의존성 (core, Jackson 등)
        FileCollection testDependencies = project.getConfigurations()
            .getByName(testSourceSet.getRuntimeClasspathConfigurationName());
        PluginServices services = project.getObjects().newInstance(PluginServices.class);
        TaskProvider<Test> test = project.getTasks().named(PluginConstants.TEST_TASK, Test.class);
        Provider<Directory> asyncApiOutput = project.getLayout().getBuildDirectory()
            .dir(PluginConstants.ASYNC_API_OUTPUT_DIR);

        TestTaskSetup.configure(project, test, extension, services);
        SourceDocsTasks.configure(project, extension, test);
        LibraryDependencies.configure(project, extension);
        WebFluxTestClasspath.configure(project, extension);
        TaskProvider<JavaExec> index = IndexTasks.registerIndex(project, extension, test, testClasspath);
        CommonResponsesIndex.configure(project, extension, index);
        TaskProvider<Sync> sources = IndexTasks.registerAsciidocSources(project, extension, test, index);
        TaskProvider<JavaExec> asyncApi =
            AsyncApiTasks.register(project, extension, test, testDependencies, asyncApiOutput, services);
        TaskProvider<Sync> apiDocs = ApiDocsTasks.registerApiDocs(project, extension, asyncApi, asyncApiOutput);
        ApiDocsTasks.registerAppDocs(project, extension, apiDocs);

        GenerateOnRun.configure(project, extension, test, apiDocs);
        AsciidoctorIntegration.configure(project, extension, sources, apiDocs);
        OpenApiIntegration.configure(project, extension, test, apiDocs);
        ApiDocsAggregation.configureProducer(project, extension, apiDocs);
        WireMockStubs.configure(project, extension, test);
        ExportTasks.register(project, extension, asyncApiOutput);
        SpringBootIntegration.configureBootJar(project, extension, apiDocs);
        SpringBootIntegration.configureStartupLog(project, extension);
    }
}
