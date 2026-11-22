// Kotest 확장 모듈 공통 (kotest: Kotest 6.x, kotest5: Kotest 5.x).
// 두 모듈은 같은 패키지·같은 공개 API 를 다른 Kotest 버전으로 컴파일한다. Kotest 버전과 상관없는 코드(이름 규칙, 스펙 문서 기록,
// 실행 상태, Enum 확장)와 테스트는 kotest-common/ 에 한 벌만 두고 두 모듈의 소스셋에 함께 넣는다. kotest-common 은 Gradle
// 프로젝트가 아니고 따로 배포하지도 않는다 (각 모듈 jar 에 같이 컴파일된다). 모듈에는 Kotest API 에 닿는 얇은 어댑터만 둔다.
plugins {
    id("restdocs.kotlin-library")
}

val sharedSources = rootProject.layout.projectDirectory.dir("kotest-common/src")
val sharedMain = sharedSources.dir("main/kotlin")
val sharedTest = sharedSources.dir("test/kotlin")

kotlin {
    sourceSets.named("main") { kotlin.srcDir(sharedMain) }
    sourceSets.named("test") { kotlin.srcDir(sharedTest) }
}

// detekt 태스크는 src/main/kotlin 등 기본 디렉터리만 읽는다. 공유 소스도 검사한다 (ktlint 는 소스셋을 따르므로 이미 포함)
detekt {
    source.from(sharedMain, sharedTest)
}

dokka {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(sharedMain.asFile)
            remoteUrl("${property("projectUrl")}/tree/main/kotest-common/src/main/kotlin")
        }
    }
}

// kotest(6.x)·kotest5(5.x) 는 같은 패키지·같은 클래스를 담으므로 함께 들어가면 먼저 오는 쪽이 이겨 NoSuchMethodError 로 실패한다.
// 두 모듈이 같은 capability 를 내보내 Gradle 이 둘을 함께 고르면 충돌로 알린다 (Gradle 모듈 메타데이터로 전달되므로 Maven POM 만
// 읽는 소비자에게는 효과가 없다). capability 를 직접 선언하면 기본 capability 가 사라지므로 기본값도 함께 둔다:
// 같은 빌드의 project(":kotest") 는 프로젝트 이름, 배포된 모듈은 artifactId 를 기본 capability 로 찾는다.
val publishedModule = the<PublishedModuleExtension>()
val kotestExtensionCapability = "io.github.kardx-project:kardx-kotest-extension"

configurations.matching { it.name == "apiElements" || it.name == "runtimeElements" }
    .configureEach {
        outgoing {
            capability("${project.group}:${project.name}:${project.version}")
            capability(publishedModule.artifactId.map { "${project.group}:$it:${project.version}" })
            capability("$kotestExtensionCapability:${project.version}")
        }
    }

// 위 capability 는 Maven POM 으로 옮길 수 없다는 배포 경고를 끈다 (의도한 것이고 Gradle 소비자만 쓴다)
publishing.publications.withType<MavenPublication>()
    .configureEach {
        suppressPomMetadataWarningsFor("apiElements")
        suppressPomMetadataWarningsFor("runtimeElements")
    }
