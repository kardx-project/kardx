import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// 배포하는 Kotlin 라이브러리 공통 (annotations, core, boot).
// explicitApi() 는 모듈 build 파일이 직접 켠다 (공개 API 정책을 모듈에서 바로 보이게).
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("restdocs.code-quality")
    id("restdocs.published")
    // 공개 API 를 api/<모듈>.api 로 고정한다. check 가 apiCheck 를 돌린다. 바꿀 때는 ./gradlew apiDump
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    // javadoc jar 를 Dokka(Kotlin 문서) 결과물로 채운다. 기본 javadoc 은 Kotlin 소스를 읽지 못해 비어 있다
    id("org.jetbrains.dokka-javadoc")
}

val publishedModule = the<PublishedModuleExtension>()

// 지원하는 Kotlin 하한 (카탈로그 kotlin-baseline, 예: 2.2.21). 메타데이터 버전은 그 major.minor 다
val kotlinBaseline = versionCatalog.requiredVersion("kotlin-baseline")
val kotlinBaselineLanguage = KotlinVersion.fromVersion(
    kotlinBaseline.split('.')
        .take(2)
        .joinToString("."),
)

base {
    archivesName.set(publishedModule.artifactId)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    // 배포 POM 의 kotlin-stdlib 버전. 사용자 프로젝트의 stdlib 를 컴파일러 버전으로 끌어올리지 않는다.
    coreLibrariesVersion = kotlinBaseline
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict"))
        jvmTarget.set(JvmTarget.JVM_17)
        // 최신 Kotlin 으로 컴파일하되, 하한 버전(kotlin-baseline) 사용자도 읽을 수 있는 메타데이터로 낸다.
        languageVersion.set(kotlinBaselineLanguage)
        apiVersion.set(kotlinBaselineLanguage)
        // 인터페이스 기본 구현을 JVM default 메서드로만 낸다. 호환용 DefaultImpls 클래스를 공개 ABI 에 남기지 않는다
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
    }
}

// 테스트는 Kotest(JUnit Platform). 지정하지 않으면 Gradle 기본(JUnit 4)이 테스트를 하나도 찾지 못하고 성공한다
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Spring 설정 메타데이터(IDE 자동 완성). 소스에는 additional-spring-configuration-metadata.json 하나만 둔다
// (IntelliJ 가 같은 프로젝트 모듈에서 읽는 이름). 빌드 출력과 jar 에는 표준 이름 spring-configuration-metadata.json
// 으로만 넣는다 (IntelliJ·Spring Boot 가 jar 의존성에서 읽는 이름). 두 이름이 함께 있으면 자동 완성이 중복된다.
// Spring Boot 설정 처리기가 만드는 배치(소스 additional-, 출력 표준 이름)와 같다. 파일이 없는 모듈에서는 하는 일이 없다
tasks.named<ProcessResources>("processResources") {
    filesMatching("META-INF/additional-spring-configuration-metadata.json") {
        name = "spring-configuration-metadata.json"
    }
}

tasks.named<Jar>("javadocJar") {
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

// 모듈 이름(moduleName)과 모듈 설명(includes)은 모듈 build 파일이 정한다
val sourceRepositoryPath = projectDir.relativeTo(rootDir)
    .invariantSeparatorsPath

dokka {
    moduleName.convention(publishedModule.artifactId)
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("${property("projectUrl")}/tree/main/$sourceRepositoryPath/src/main/kotlin")
        }
    }
}

tasks.withType<Jar>().configureEach {
    // 오류 메시지는 미리 문자열로 만든다. provider 람다가 project 를 잡으면 configuration cache 에 넣을 수 없다
    val missingModuleName = "${project.path}: publishedModule.moduleName 이 없습니다"

    manifest {
        attributes(
            "Implementation-Title" to publishedModule.artifactId,
            "Implementation-Version" to project.version,
            "Automatic-Module-Name" to publishedModule.moduleName.orElse(
                provider { error(missingModuleName) },
            ),
        )
    }
}
