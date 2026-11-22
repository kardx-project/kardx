plugins {
    id("restdocs.kotest-module")
}

// Kotest 6.x 스펙 문서화 확장. 사용자 테스트 클래스패스(testImplementation)에 core 와 함께 들어간다.
// Kotest 5.x 용은 kotest5 모듈(kardx-kotest5)이다. 버전과 상관없는 소스는 kotest-common/ 에 있다.
publishedModule {
    artifactId = "kardx-kotest"
    description =
        "Kotest extension for kardx: per-spec AsciiDoc documents from given/when/then tests"
    moduleName = "io.github.kardx.kotest"
}

// Kotest 엔진과 Kotest Spring 확장은 사용자 프로젝트가 가져오므로 compileOnly 다 (Kotest 6.x API 로 컴파일).
// Spring 확장은 클래스가 있을 때만 쓴다 (SpringContexts). core 는 Kotest 에 의존하지 않는다.
dependencies {
    api(project(":core"))

    compileOnly(libs.kotest.framework.engine)
    compileOnly(libs.kotest.extensions.spring)
    compileOnly(libs.spring.test)
    compileOnly(libs.spring.core)
    compileOnly(libs.spring.context)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.restdocs.mockmvc)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.jackson.databind)
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
