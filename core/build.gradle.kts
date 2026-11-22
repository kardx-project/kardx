plugins {
    id("restdocs.kotlin-library")
    // 테스트의 @Serializable 클래스에 serializer 를 만든다. 본체에는 @Serializable 클래스가 없어 영향이 없다
    alias(libs.plugins.kotlin.serialization)
}

publishedModule {
    artifactId = "kardx"
    description = "Kotlin DSL, reflection-based field extraction and AsciiDoc / OpenAPI / AsyncAPI generation " +
        "for Spring REST Docs 3 and 4"
    moduleName = "io.github.kardx"
}

// spring-messaging 은 버전 카탈로그에 없어 Spring Framework 버전으로 좌표를 만든다 (카탈로그는 gradle-plugin 과 같이 쓴다)
val springMessaging = "org.springframework:spring-messaging:${libs.versions.spring.framework.get()}"

// kardx 본체. Spring REST Docs 3.0 / Spring Framework 6 API 로 컴파일한다.
// 사용하는 API 는 REST Docs 4.0 / Spring Framework 7 과 바이너리 호환된다 (samples/spring-boot-4 가 검증).
// REST Docs 는 지원 하한(3.0) 으로 선언하고, 실제 버전은 사용자 프로젝트의 Spring Boot BOM 이 정한다.
dependencies {
    api(project(":annotations"))
    api(libs.kotlin.reflect)

    api(libs.spring.restdocs.core)
    // MockMvc 진입점(mockmvc·sse 패키지)만 쓴다. 사용자가 MockMvc 를 쓰면 spring-restdocs-mockmvc 를 직접(또는
    // io.github.kardx-project 플러그인이) 넣는다. 그래서 WebFlux 프로젝트에 서블릿 쪽 의존성이 끌려오지 않는다
    compileOnly(libs.spring.restdocs.mockmvc)
    // 테스트 지원 API (JUnit·Spring 테스트 컨텍스트, WebTestClient). spring-restdocs-mockmvc 가 끌고 오던 것을 직접 노출한다
    api(libs.spring.test)
    compileOnly(libs.spring.web)
    compileOnly(libs.spring.websocket)
    // STOMP 테스트 클라이언트(websocket/StompTestClient)만 쓴다. STOMP 앱(spring-boot-starter-websocket)에는 이미 있다
    compileOnly(springMessaging)
    compileOnly(libs.spring.core)
    compileOnly(libs.spring.context)
    compileOnly(libs.junit.jupiter.api)
    compileOnly(libs.jakarta.validation.api)
    compileOnly(libs.epages.restdocs.api.spec)
    compileOnly(libs.jakarta.servlet.api)
    // WebTestClient 진입점(webtestclient 패키지)만 쓴다. WebFlux 를 쓰지 않으면 그 패키지의 클래스를 로드하지 않는다
    compileOnly(libs.spring.restdocs.webtestclient)
    compileOnly(libs.spring.webflux)
    // MockMvcTester 진입점(MvcTestResultExtensions.kt)만 쓴다. Spring 6.2 미만에서는 그 파일의 클래스를 로드하지 않는다
    compileOnly(libs.assertj.core)
    // @Serializable 클래스의 serial descriptor(이름·생략 가능·sealed 판별자)만 읽는다. 클래스패스에 없으면 그 코드를 로드하지 않는다
    compileOnly(libs.kotlinx.serialization.core)

    testImplementation(libs.junit.jupiter)
    // AutoRestDocumentationExtension 의 콜백 순서를 실제 JUnit 실행으로 확인한다 (ConfigurationRestoreTest)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.jakarta.validation.api)
    testImplementation(libs.spring.restdocs.mockmvc)
    testImplementation(libs.spring.websocket)
    testImplementation(springMessaging)
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.json.path)
    testImplementation(libs.swagger.annotations)
    testImplementation(libs.spring.data.commons)
    testImplementation(libs.spring.hateoas)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.assertj.core)
    testImplementation(libs.spring.restdocs.webtestclient)
    testImplementation(libs.spring.webflux)
    // WebTestClient 의 expectBody<T>() 구현(DefaultBodySpec)이 hamcrest 를 참조한다
    testRuntimeOnly(libs.hamcrest)
    // epages 본체는 spring-boot-starter-web 을 끌고 온다. Boot 의 설정 메타데이터가 ConfigurationMetadataTest 를 흐리므로 뺀다
    testImplementation(libs.epages.restdocs.api.spec) {
        exclude(group = "org.springframework.boot")
    }
}

// MockMvc·서블릿 없이 쓰는 진입점(webtestclient, websocket, extractor, junit, lifecycle 등)이 그 클래스 없이 로드·동작하는지
// 본다. 사용자처럼 이 모듈의 배포 의존성(project(":core"))만 받고 spring-restdocs-mockmvc, spring-webmvc,
// jakarta.servlet-api 는 넣지 않는다.
val noServletTest: SourceSet by sourceSets.creating

// 추가 소스셋의 폴더는 본체·테스트 폴더 아래에 둔다 (src/main/<이름>, src/test/<이름>).
// 기본 소스셋의 srcDir 은 src/main/kotlin·src/test/kotlin 이라 이 하위 폴더를 집어 가지 않는다
fun SourceSet.useDirectory(directory: String) {
    java.setSrcDirs(listOf("$directory/java"))
    resources.setSrcDirs(listOf("$directory/resources"))
    kotlin.setSrcDirs(listOf("$directory/kotlin", "$directory/java"))
}

noServletTest.useDirectory("src/test/noServlet")

dependencies {
    "noServletTestImplementation"(project(":core"))
    "noServletTestImplementation"(libs.kotest.runner.junit5)
    "noServletTestImplementation"(libs.kotest.assertions.core)
    "noServletTestImplementation"(libs.junit.jupiter)
    "noServletTestImplementation"(libs.junit.platform.launcher)
    "noServletTestImplementation"(libs.spring.restdocs.webtestclient)
    "noServletTestImplementation"(libs.spring.webflux)
    // WebFlux 앱과 STOMP(서블릿 스택이 아닌 spring-websocket 의 메시지 API) 프로젝트가 가진 것
    "noServletTestImplementation"(libs.spring.context)
    "noServletTestImplementation"(libs.spring.websocket)
    "noServletTestImplementation"(springMessaging)
    "noServletTestImplementation"(libs.jackson.databind)
    "noServletTestRuntimeOnly"(libs.hamcrest)
}

val noServletTestTask = tasks.register<Test>("noServletTest") {
    description = "Runs entry points that must work without MockMvc and the Servlet API on the classpath."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = noServletTest.output.classesDirs
    classpath = noServletTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(noServletTestTask)
}

detekt {
    source.from("src/test/noServlet/kotlin")
}

// RestTestClient 진입점(Spring Framework 7 / Boot 4 전용). core 본체는 Spring 6 / REST Docs 3 API 로 컴파일하므로, Spring 7 에만
// 있는 RestTestClient 를 쓰는 코드는 따로 Spring 7 / REST Docs 4 로 컴파일해 같은 jar 에 넣는다. 이 패키지(resttestclient)의
// 클래스는 사용자가 RestTestClient 로 andDocument 를 부를 때만 로드되므로 Boot 3 프로젝트에서는 로드되지 않는다.
val restTestClient: SourceSet by sourceSets.creating
val restTestClientTest: SourceSet by sourceSets.creating
restTestClient.useDirectory("src/main/restTestClient")
restTestClientTest.useDirectory("src/test/restTestClient")
val springFramework7 = "7.1.0"
val restDocs4 = "org.springframework.restdocs:spring-restdocs-core:${libs.versions.spring.restdocs.v4.get()}"

// 본체 클래스와 그 의존성 위에 Spring 7 판을 얹는다 (같은 좌표는 높은 버전이 이긴다). 어댑터 본체는 공개 SPI
// (io.github.kardx.client.ClientDocumentation)만 쓰도록 friend 로 두지 않는다 — SPI 가 충분한지 컴파일로 검사한다.
// 테스트는 본체 internal 을 쓰므로 friend 로 둔다.
// associateWith 는 본체 jar 에 기대는데 그 jar 가 이 출력을 담으므로 순환이 생겨 쓰지 않는다
restTestClient.compileClasspath += sourceSets.main.get().output
restTestClientTest.compileClasspath += sourceSets.main.get().output + restTestClient.output
restTestClientTest.runtimeClasspath += sourceSets.main.get().output + restTestClient.output

configurations.named("restTestClientCompileOnly") {
    extendsFrom(configurations.api.get(), configurations.implementation.get(), configurations.compileOnly.get())
}

configurations.named("restTestClientTestImplementation") {
    extendsFrom(configurations.api.get(), configurations.implementation.get())
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileRestTestClientKotlin") {
    // kotlin { explicitApi() } 는 main 컴파일에만 걸린다. 같은 jar 로 배포하는 이 소스 세트에도 같은 규칙을 건다
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileRestTestClientTestKotlin") {
    friendPaths.from(sourceSets.main.get().output.classesDirs, restTestClient.output.classesDirs)
}

dependencies {
    "restTestClientCompileOnly"("org.springframework:spring-test:$springFramework7")
    "restTestClientCompileOnly"("org.springframework:spring-web:$springFramework7")
    "restTestClientCompileOnly"(restDocs4)

    "restTestClientTestImplementation"("org.springframework:spring-test:$springFramework7")
    "restTestClientTestImplementation"("org.springframework:spring-webmvc:$springFramework7")
    "restTestClientTestImplementation"(restDocs4)
    "restTestClientTestImplementation"("jakarta.servlet:jakarta.servlet-api:6.1.0")
    "restTestClientTestImplementation"(libs.jackson.databind)
    "restTestClientTestImplementation"(libs.kotest.runner.junit5)
    "restTestClientTestImplementation"(libs.kotest.assertions.core)
    "restTestClientTestImplementation"(libs.junit.jupiter)
    "restTestClientTestRuntimeOnly"(libs.junit.platform.launcher)
}

val restTestClientTestTask = tasks.register<Test>("restTestClientTest") {
    description = "Runs the RestTestClient entry point against Spring Framework 7 and REST Docs 4."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = restTestClientTest.output.classesDirs
    classpath = restTestClientTest.runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(restTestClientTestTask)
}

tasks.jar {
    from(restTestClient.output)
}

tasks.named<Jar>("sourcesJar") {
    from(restTestClient.allSource)
}

apiValidation {
    additionalSourceSets.add("restTestClient")
}

detekt {
    source.from("src/main/restTestClient/kotlin", "src/test/restTestClient/kotlin")
}

kotlin {
    // 공개 API 는 public 을 명시해야 한다. 실수로 내부 구현이 공개되는 것을 막는다.
    explicitApi()
    // 실험 API(@ExperimentalRestDocsApi)는 이 모듈이 구현하므로 모듈 안(본체·테스트)에서는 opt-in 없이 쓴다
    compilerOptions.optIn.add("io.github.kardx.ExperimentalRestDocsApi")
}

// Dokka(javadoc jar) 공통 설정은 restdocs.kotlin-library 에 있다. 여기에는 core 의 모듈 이름과 설명만 둔다.
dokka {
    moduleName.set("kardx")
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
    }
}
