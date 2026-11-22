// Maven 플러그인 (packaging maven-plugin). 로직은 tooling(빌드 도구와 무관한 모듈)에 있고 여기는 Maven 목표(mojo)만 있다.
// 플러그인 설명자(META-INF/maven/plugin.xml)는 de.benediktritter.maven-plugin-development 가 mojo 어노테이션에서 만든다.
plugins {
    `java-library`
    id("restdocs.published")
    alias(libs.plugins.maven.plugin.development)
}

publishedModule {
    artifactId = "kardx-maven-plugin"  // 아래 pluginArtifactId 와 같게
    description = "Maven plugin for kardx: OpenAPI, viewer, coverage, spec check and index " +
        "after the documentation tests"
}

java {
    // Maven 3.9 는 Java 8 에서도 돌지만 tooling 과 epages 생성기가 Java 17 이다
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

dependencies {
    // 루트 settings 의 includeBuild("tooling") 가 이 좌표를 tooling 빌드로 바꾼다
    implementation("${project.group}:kardx-tooling:${project.version}")
    // OpenAPI 기본 스펙·Postman Collection 을 만드는 epages 생성기 (Gradle 에서는 epages 플러그인이 만든다)
    runtimeOnly(libs.epages.restdocs.api.spec.openapi3.generator)
    runtimeOnly(libs.epages.restdocs.api.spec.postman.generator)
    // Maven 이 실행 시점에 주는 API (provided)
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)
    testCompileOnly(libs.maven.plugin.annotations)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val pluginArtifactId = "kardx-maven-plugin"

base {
    archivesName = pluginArtifactId
}

mavenPlugin {
    // 설명자(plugin.xml)의 좌표는 배포 좌표와 같아야 한다 (기본값은 프로젝트 이름 maven-plugin)
    artifactId = pluginArtifactId
    name = pluginArtifactId
    description = "Generates OpenAPI, viewer, coverage and index after the kardx tests"
    // mvn kardx:api-docs (settings.xml 의 pluginGroups 에 io.github.kardx-project 가 있을 때)
    goalPrefix = "kardx"
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            packaging = "maven-plugin"
        }
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.kardx.maven")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}
