plugins {
    `java-platform`
    id("restdocs.published")
}

publishedModule {
    artifactId = "kardx-bom"
    description = "Bill of materials for kardx"
}

dependencies {
    constraints {
        api(project(":annotations"))
        api(project(":core"))
        api(project(":boot"))
        api(project(":kotest"))
        api(project(":kotest5"))
        // 빌드 도구와 무관한 후처리·명령줄 (included build "tooling"). Gradle·Maven 플러그인은 의존이 아니라서 넣지 않는다
        // (BOM 의 dependencyManagement 는 플러그인 판을 정하지 않는다)
        api("${project.group}:kardx-tooling:${project.version}")
    }
}
