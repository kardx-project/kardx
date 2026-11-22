plugins {
    id("restdocs.kotest-module")
}

// Kotest 5.x 스펙 문서화 확장. kotest 모듈(Kotest 6.x)과 같은 패키지·같은 공개 API 이므로 사용자는 의존성 좌표만 고른다.
// 두 아티팩트를 한 클래스패스에 함께 넣으면 안 된다 (같은 클래스가 두 벌). 버전과 상관없는 소스는 kotest-common/ 에 있다.
publishedModule {
    artifactId = "kardx-kotest5"
    description =
        "Kotest 5.x extension for kardx: per-spec AsciiDoc documents from given/when/then tests"
    // kotest 모듈과 같다. 패키지가 같고, 두 jar 는 capability 충돌로 한 클래스패스에 함께 들어오지 않는다
    moduleName = "io.github.kardx.kotest"
}

// Kotest 5.x API 와 그 Spring 확장(io.kotest.extensions:kotest-extensions-spring)은 사용자 프로젝트가 가져오므로 compileOnly 다.
dependencies {
    api(project(":core"))

    compileOnly(libs.kotest5.framework.api)
    compileOnly(libs.kotest5.extensions.spring)
    compileOnly(libs.spring.test)
    compileOnly(libs.spring.core)
    compileOnly(libs.spring.context)

    testImplementation(libs.kotest5.runner.junit5)
    testImplementation(libs.kotest5.assertions.core)
    testImplementation(libs.kotest5.framework.datatest)
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
