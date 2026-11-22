// 컨벤션 플러그인 빌드. 루트 settings 의 pluginManagement { includeBuild("build-logic") } 로 포함한다.
rootProject.name = "build-logic"

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    // 라이브러리와 같은 버전 카탈로그를 쓴다. 플러그인 버전은 여기 한 곳에서 정한다.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
