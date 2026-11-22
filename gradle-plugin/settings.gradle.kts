// 샘플이 pluginManagement { includeBuild("gradle-plugin") } 로 바로 쓰는 composite build.
rootProject.name = "kardx-gradle-plugin"

// 빌드 도구와 무관한 후처리 로직(io.github.kardx-project:kardx-tooling). 좌표를 이 빌드로 바꿔 쓴다.
includeBuild("../tooling")

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
