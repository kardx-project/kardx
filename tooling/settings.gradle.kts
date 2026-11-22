// 빌드 도구와 무관한 문서 후처리(OpenAPI 보강, 검사, 범위 보고서, 뷰어, .http, 소스 설명 색인)와 명령줄 진입점.
// Gradle 플러그인(gradle-plugin)이 설정 단계에서 이 jar 를 써야 하므로 루트 빌드의 하위 프로젝트가 아니라 따로 포함하는
// 빌드다. gradle-plugin/settings.gradle.kts 와 루트 settings.gradle.kts 가 includeBuild("tooling") 으로 포함하고,
// 좌표 io.github.kardx-project:kardx-tooling 을 이 빌드로 바꿔 쓴다.
rootProject.name = "kardx-tooling"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    // 라이브러리와 같은 버전 카탈로그를 쓴다.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
