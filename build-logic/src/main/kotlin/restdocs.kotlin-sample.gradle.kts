// 샘플 애플리케이션 공통 (배포하지 않음). Kotlin·Spring Boot 플러그인은 샘플마다 버전이 달라 샘플 build 파일이 직접 적용한다.
plugins {
    id("restdocs.code-quality")
}

// io.github.kardx-project 플러그인은 라이브러리를 배포 좌표(io.github.kardx-project:<아티팩트>:<플러그인 버전>)로 넣는다.
// 샘플은 mavenLocal 의 지난 jar 가 아니라 같은 빌드의 소스로 돌아야 하므로 그 좌표를 프로젝트로 바꾼다.
val libraryProjects = mapOf(
    "kardx" to ":core",
    "kardx-annotations" to ":annotations",
    "kardx-boot" to ":boot",
    "kardx-kotest" to ":kotest",
    "kardx-kotest5" to ":kotest5",
)

configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        libraryProjects.forEach { (artifactId, path) ->
            substitute(module("io.github.kardx-project:$artifactId"))
                .using(project(path))
                .because("저장소 안의 샘플은 라이브러리 소스로 빌드한다")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
