import java.util.Properties

plugins {
    `java-gradle-plugin`
    `maven-publish`
    // Gradle Plugin Portal 배포(publishPlugins). 키는 GRADLE_PUBLISH_KEY·GRADLE_PUBLISH_SECRET (release.yml)
    alias(libs.plugins.plugin.publish)
}

// 버전·그룹·저장소 URL 은 라이브러리와 같다.
val rootProperties = Properties().apply { file("../gradle.properties").inputStream().use(::load) }
group = rootProperties.getProperty("group")
version = rootProperties.getProperty("version")
val projectUrl: String = rootProperties.getProperty("projectUrl")
val scmConnection = "scm:git:$projectUrl.git"

java {
    // Gradle 이 Kotlin 런타임을 내장하므로 Kotlin stdlib 충돌을 피하려고 플러그인은 Java 로 작성한다.
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach { options.release = 17 }

dependencies {
    // 스펙 후처리·검사·보고서 로직. Gradle 과 무관한 모듈이라 Maven 플러그인·명령줄과 같이 쓴다.
    // Groovy 는 Gradle 이 내장하므로 빼고 Gradle 의 것을 쓴다 (다른 Groovy 가 플러그인 클래스패스에 올라오지 않게)
    implementation("$group:kardx-tooling:$version") {
        exclude(group = "org.apache.groovy")
    }
    testImplementation(libs.snakeyaml)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // GradleCompatibilityFunctionalTest 가 돌릴 Gradle 버전 (쉼표 구분). 없으면 그 테스트는 돌지 않는다
    systemProperty("restdocs.gradleVersions", providers.gradleProperty("gradleVersions").getOrElse(""))
}

// TestKit 픽스처가 테스트 클래스의 가짜 epages 플러그인(com.epages.restdocs-api-spec)도 plugins { } 로 쓸 수 있게 한다.
tasks.pluginUnderTestMetadata {
    pluginClasspath.from(sourceSets.test.map { it.output })
}

// 플러그인이 사용자 앱에 넣는 런타임 모듈(kardx-boot)의 버전. 플러그인과 같다.
tasks.processResources {
    val pluginVersion = version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("**/kardx-plugin.properties") { expand("version" to pluginVersion) }
}

// 캐시 어노테이션 누락 같은 문제도 check 에서 잡는다 (Plugin Portal 도 같은 검사를 한다)
tasks.validatePlugins {
    enableStricterValidation = true
}

gradlePlugin {
    website = projectUrl
    vcsUrl = projectUrl
    plugins {
        create("kardx") {
            id = "io.github.kardx-project"
            implementationClass = "io.github.kardx.gradle.KardxPlugin"
            displayName = "kardx"
            description = "Wires Spring REST Docs, AsciiDoc, OpenAPI and AsyncAPI generation for kardx"
            tags = listOf("kotlin", "spring", "restdocs", "openapi", "asyncapi", "documentation")
        }
    }
}

publishing {
    // 플러그인 본체(pluginMaven)의 이름·설명. 플러그인 마커(*PluginMarkerMaven) POM 은 java-gradle-plugin 이
    // gradlePlugin 의 displayName·description 으로 채우므로 덮어쓰지 않는다.
    publications.withType<MavenPublication>()
        .matching { it.name == "pluginMaven" }
        .configureEach {
            pom {
                name = rootProject.name
                description = "Gradle plugin for kardx: wires Spring REST Docs, AsciiDoc, " +
                    "OpenAPI and AsyncAPI generation"
            }
        }
    publications.withType<MavenPublication>().configureEach {
        // 라이브러리 모듈의 POM(build-logic/src/main/kotlin/restdocs.published.gradle.kts)과 같은 값을 쓴다.
        // 이 빌드는 build-logic 을 포함하지 않으므로 값만 맞춘다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
        // (build-logic 을 포함해 공유하면 그 빌드의 Kotlin·detekt·Dokka 플러그인이 이 빌드 클래스패스에 올라오고
        // projectUrl 을 읽는 방식도 달라져, 스무 줄을 공유하는 이득보다 복잡도가 크다.)
        pom {
            url = projectUrl
            inceptionYear = "2026"
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

// Maven Central 업로드 묶음을 만드는 파일 저장소와 서명.
// 플러그인 사용자는 Gradle Plugin Portal(publishPlugins)에서 받는다. 그래도 플러그인 본체(kardx-gradle-plugin)와 마커
// (io.github.kardx-project.gradle.plugin, packaging pom)를 Central 에도 같이 올린다: 해가 없고,
// pluginManagement { repositories { mavenCentral() } } 만 쓰는 사용자도 플러그인을 찾을 수 있다. 라이브러리 모듈(build-logic/src/main/kotlin/restdocs.published.gradle.kts)과
// 같은 폴더(저장소 루트의 build/staging-deploy)·같은 속성 이름을 쓴다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
publishing {
    repositories {
        maven {
            name = "staging"
            url = uri(layout.projectDirectory.dir("../build/staging-deploy"))
        }
    }
}

// 키가 있을 때만 서명한다 (CI 의 ORG_GRADLE_PROJECT_signingInMemoryKey). 없으면 로컬 빌드는 전과 같다.
// signing 플러그인도 이때만 적용한다: com.gradle.plugin-publish 는 signing 이 적용돼 있으면 모든 배포물에 서명 태스크를
// 붙이므로, 키 없이 적용하면 publishToMavenLocal·publishPlugins 가 "no configured signatory" 로 실패한다.
val signingKey = providers.gradleProperty("signingInMemoryKey")

if (signingKey.isPresent) {
    apply(plugin = "signing")
    configure<SigningExtension> {
        useInMemoryPgpKeys(signingKey.get(), providers.gradleProperty("signingInMemoryKeyPassword").orNull)
        sign(publishing.publications)
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}
