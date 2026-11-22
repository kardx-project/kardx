plugins {
    id("restdocs.kotlin-library")
}

// 앱 실행 시 API 문서 주소를 INFO 로그로 알려 주는 런타임 모듈. 사용자 앱의 런타임 클래스패스(플러그인 기본값 developmentOnly)에 들어간다.
publishedModule {
    artifactId = "kardx-boot"
    description = "Logs API documentation URLs (REST Docs, Swagger UI, OpenAPI, AsyncAPI) when a Spring Boot app starts"
    moduleName = "io.github.kardx.boot"
}

// Spring Boot 는 사용자 앱이 가져오므로 compileOnly 다. Boot 3 API 로 컴파일하고 Boot 4 와 바이너리 호환된다.
dependencies {
    compileOnly("org.springframework.boot:spring-boot:${libs.versions.spring.boot.v3.get()}")
    compileOnly(libs.spring.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.core)
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
