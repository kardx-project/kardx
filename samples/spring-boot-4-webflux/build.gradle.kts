// Spring Boot 4.1 WebFlux + Spring REST Docs 4 (WebTestClient) + epages 0.20 + springdoc 3.2 샘플. 서블릿 스택이 없다.
//
// ./gradlew :samples:spring-boot-4-webflux:apiDocs   테스트 → index → REST Docs HTML + OpenAPI + AsyncAPI (build/api-docs)
// ./gradlew :samples:spring-boot-4-webflux:bootJar   위 결과를 jar 의 /static/docs 에 넣는다
//   /docs/index.html                   REST Docs
//   /docs/openapi3.yaml                OpenAPI 3 스펙 (HTTP)
//   /docs/asyncapi.html                SSE 이벤트 문서 (AsyncAPI 3) + asyncapi.yaml
//   /webjars/swagger-ui/index.html     위 스펙을 보여 주는 Swagger UI (springdoc WebFlux UI 는 /webjars 아래)
plugins {
    id("io.github.kardx-project") // 문서 파이프라인 + 라이브러리 의존성 (gradle-plugin, composite build)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot.v4)
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.epages.v4)
    id("restdocs.kotlin-sample") // ktlint·detekt, JUnit 플랫폼, 라이브러리 좌표 → 같은 빌드의 프로젝트 (build-logic)
}

// 라이브러리(annotations·core·kotest·boot)와 REST Docs·epages WebTestClient 클라이언트는 플러그인이 넣는다 (docs/build.md).
// exclude 도 필요 없다: epages 가 끌고 오는 spring-boot-starter-web 은 플러그인이 테스트 런타임 클래스패스에서 뺀다
// (excludeServletStarterForWebFlux). 확인은 WebApplicationTypeTest 가 한다.
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.v4.get()}"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("tools.jackson.module:jackson-module-kotlin")
    // suspend 핸들러·Flow 반환과 Reactor 타입 ↔ 코루틴 변환 (awaitSingle, asFlow)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation(libs.springdoc.webflux.ui.v4)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Kotest 스펙 문서화 (kotest/ProductSummaryDocsSpec). 엔진과 Spring 확장은 프로젝트가 넣고, 확장은 플러그인이 넣는다
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.spring)
}

// 경로·index 는 기본값이다. 아래는 기본값과 다른 것만 적는다.
kardx {
    language = "ko" // 생성 문서의 라이브러리 문구를 한국어로 (기본값 en). yml 이 아니라 여기서 정해야 플러그인 문구도 바뀐다
    sourceDocs { enabled = true } // KDoc → 필드·스키마 설명 (실험 기능, 기본 꺼짐). 메서드 KDoc 은 쓰지 않는다
    asyncApi {
        title = "kardx WebFlux 예제 이벤트 API"
        version = "0.1"
        description = "Server-Sent Events. 통과한 WebTestClient 테스트가 실제로 받은 이벤트에서 추출했다.\n\n" +
            "샘플 앱은 데모 토큰 `demo-token` 을 요구한다. 인증의 토큰 칸에 `demo-token` 을 넣고 " +
            "'WebSocket·SSE 는 쿼리 파라미터로 보내기' 를 켜면 `?access_token=demo-token` 으로 보낸다 " +
            "(브라우저 EventSource 는 헤더를 붙일 수 없다)."
        server("http", "http://localhost:8080")
        bearerAuth() // components.securitySchemes.bearerAuth. 뷰어의 연결 시험에 토큰 입력 칸이 생긴다
    }
}

// format(yaml)·서버("/")는 플러그인 기본값이다
openapi3 {
    title = "kardx WebFlux 예제 API"
    description = "통과한 WebTestClient 테스트의 REST Docs 스니펫에서 추출한 HTTP API 스펙.\n\n" +
        "Server-Sent Events 는 [SSE 이벤트 문서](/docs/asyncapi.html), " +
        "전체 문서는 [REST Docs](/docs/index.html) 에서 볼 수 있습니다."
    version = "0.1"
}
