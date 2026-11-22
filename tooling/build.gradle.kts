import java.util.Properties

plugins {
    `java-library`
    `maven-publish`
}

// 버전·그룹·저장소 URL 은 라이브러리와 같다 (included build 는 루트의 gradle.properties 를 읽지 않는다).
val rootProperties = Properties().apply { file("../gradle.properties").inputStream().use(::load) }
group = rootProperties.getProperty("group")
version = rootProperties.getProperty("version")
val projectUrl: String = rootProperties.getProperty("projectUrl")
val scmConnection = "scm:git:$projectUrl.git"

java {
    // Gradle 플러그인이 이 jar 를 쓰므로 플러그인과 같이 Java 17 로 컴파일하고 Kotlin 을 쓰지 않는다.
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

dependencies {
    // openapi3.yaml 읽기·쓰기
    implementation(libs.snakeyaml)
    // JSON 읽기·쓰기. Gradle 은 Groovy 를 내장하므로 Gradle 플러그인은 이 의존을 빼고(exclude) Gradle 의 것을 쓴다.
    // Maven 플러그인·명령줄은 이 의존을 그대로 받는다
    api(libs.groovy.json)
    // 명령줄·Maven 의 OpenAPI 기본 스펙·Postman Collection 생성(internal.epages). Gradle 은 epages 플러그인이 만든다.
    // 쓰는 쪽(Maven 플러그인, 명령줄 jar)이 런타임 의존으로 넣는다
    compileOnly(libs.epages.restdocs.api.spec.openapi3.generator)
    compileOnly(libs.epages.restdocs.api.spec.postman.generator)
    compileOnly(libs.jackson3.databind)
    compileOnly(libs.jackson3.module.kotlin)
    testImplementation(libs.epages.restdocs.api.spec.openapi3.generator)
    testImplementation(libs.epages.restdocs.api.spec.postman.generator)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// 명령줄 실행용 jar (java -jar kardx-tooling-<버전>-all.jar …): tooling, 그 런타임 의존, epages 생성기.
// Maven 플러그인은 이 jar 가 아니라 보통 jar 와 의존 목록을 쓴다.
val cliRuntime by configurations.creating {
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
}

dependencies {
    cliRuntime(libs.epages.restdocs.api.spec.openapi3.generator)
    cliRuntime(libs.epages.restdocs.api.spec.postman.generator)
    // epages 의존(swagger-core)이 SLF4J 를 쓴다. 명령줄에서는 그 로그를 버린다 ("No SLF4J providers" 경고도 없앤다)
    cliRuntime(libs.slf4j.nop)
}

val allJar by tasks.registering(Jar::class) {
    group = BasePlugin.BUILD_GROUP
    description = "Assembles the runnable command line jar (tooling with all runtime dependencies)."
    archiveClassifier = "all"
    manifest {
        attributes(
            "Main-Class" to "io.github.kardx.tooling.cli.ApiDocsCli",
            "Implementation-Version" to version,
        )
    }
    from(sourceSets.main.map { it.output })
    from(cliRuntime.elements.map { files -> files.map { zipTree(it) } })
    // 서명·모듈 정보는 합친 jar 에서 맞지 않는다. 같은 경로의 파일은 먼저 온 것(tooling)을 쓴다
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class", "META-INF/versions/*/module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.assemble { dependsOn(allJar) }

tasks.jar {
    manifest {
        attributes(
            "Automatic-Module-Name" to "io.github.kardx.tooling",
            "Main-Class" to "io.github.kardx.tooling.cli.ApiDocsCli",
            "Implementation-Version" to version,
        )
    }
}

// 공개 API 는 io.github.kardx.tooling 패키지(명령줄 진입점, 예외)뿐이다. internal 패키지는 Gradle·Maven 플러그인이 쓰는
// 구현이라 호환을 약속하지 않는다. binary-compatibility-validator 는 Kotlin 프로젝트에만 붙으므로 javap 로 같은 일을 한다:
// apiDump 가 공개 클래스의 public·protected 선언을 api/<프로젝트 이름>.api 에 쓰고, check 의 apiCheck 가 비교한다.
val apiFile = layout.projectDirectory.file("api/${project.name}.api")
val apiDumpFile = layout.buildDirectory.file("api/${project.name}.api")
val javap = javaToolchains.compilerFor(java.toolchain)
    .map { it.metadata.installationPath.file("bin/javap").asFile.absolutePath }
val publicClasses = sourceSets.main.map { main ->
    main.output.classesDirs.asFileTree.matching { include("io/github/kardx/tooling/*.class", "io/github/kardx/tooling/cli/*.class") }
}

val apiBuild by tasks.registering {
    description = "Writes the public API of the tooling (javap) to build/api."
    val classes = publicClasses
    val output = apiDumpFile
    val tool = javap

    inputs.files(classes)
        .withPropertyName("classes")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(output)
        .withPropertyName("api")
    doLast {
        val files = classes.get()
            .files
            .sortedBy { it.name }
            .map { it.absolutePath }
        val process = ProcessBuilder(listOf(tool.get(), "-public") + files)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream
            .bufferedReader()
            .readText()

        check(process.waitFor() == 0) { "javap failed: $text" }
        // javap 는 "Compiled from" 줄에 소스 파일 이름을 쓴다. 비교에 쓸모가 없으므로 빼고, 공개가 아닌 클래스(같은 패키지의
        // 도우미)는 블록째 뺀다
        val blocks = mutableListOf<List<String>>()
        var block = mutableListOf<String>()

        text.lines()
            .filterNot { it.startsWith("Compiled from") || it.isBlank() }
            .forEach { line ->
                block.add(line)

                if (line == "}") {
                    blocks.add(block)
                    block = mutableListOf()
                }
            }

        output.get()
            .asFile
            .writeText(
                blocks.filter { it.first().startsWith("public ") }
                    .joinToString("\n") { it.joinToString("\n") } + "\n",
            )
    }
}

tasks.register<Copy>("apiDump") {
    group = "other"
    description = "Updates api/${project.name}.api from the compiled public API."
    from(apiBuild)
    into(apiFile.asFile.parentFile)
}

val apiCheck by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Checks that the public API matches api/${project.name}.api (run apiDump after an intended change)."
    val expected = apiFile
    val actual = apiDumpFile

    inputs.file(expected)
        .withPropertyName("expected")
    inputs.files(apiBuild)
        .withPropertyName("actual")
    doLast {
        if (expected.asFile.readText() != actual.get().asFile.readText()) {
            throw GradleException(
                "The public API of the tooling changed. Check build/api/${expected.asFile.name} and run apiDump " +
                    "if the change is intended.",
            )
        }
    }
}

tasks.check { dependsOn(apiCheck) }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifact(allJar)
            pom {
                name = rootProject.name
                description = "Build-tool independent tooling for kardx: OpenAPI post-processing, " +
                    "spec check, coverage report, viewer and HTTP Client files, with a command line entry point"
                // 라이브러리 모듈의 POM(build-logic/src/main/kotlin/restdocs.published.gradle.kts)과 같은 값을 쓴다.
                // gradle-plugin/build.gradle.kts 와 같은 이유로 build-logic 을 포함하지 않고 값만 맞춘다.
                url = projectUrl
                inceptionYear = "2024"
                licenses {
                    license {
                        name = "Apache License 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                        distribution = "repo"
                    }
                }
                developers {
                    developer {
                        id = "devYSK"
                        name = "devYSK"
                    }
                }
                scm {
                    url = projectUrl
                    connection = scmConnection
                    developerConnection = scmConnection
                }
            }
        }
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}
