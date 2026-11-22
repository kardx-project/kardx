plugins {
    id("restdocs.kotlin-library")
}

// 사용자 운영 코드(DTO, Enum)에 붙이는 어노테이션만 담는다. 의존성은 Kotlin stdlib 뿐이다.
// 사용자는 main 에 compileOnly 로 추가하면 운영 jar 에 들어가지 않는다.
publishedModule {
    artifactId = "kardx-annotations"
    description = "Annotations (@Description, @EnumCode, @AsciiDescription) for kardx. " +
        "Add as compileOnly to main code"
    moduleName = "io.github.kardx.annotation"
}

kotlin {
    // 공개 API 는 public 을 명시해야 한다. 실수로 내부 구현이 공개되는 것을 막는다.
    explicitApi()
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("Module.md")
    }
}
