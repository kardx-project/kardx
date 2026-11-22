// Spring Boot 4.1 + Spring REST Docs 4 + epages 0.20 + springdoc 3.2 샘플
//
// ./gradlew :samples:spring-boot-4:apiDocs   테스트 → index → REST Docs HTML + OpenAPI yaml + AsyncAPI (build/api-docs)
// ./gradlew :samples:spring-boot-4:bootJar   위 결과를 jar 의 /static/docs 에 넣는다
//   /docs/index.html         REST Docs
//   /docs/openapi3.yaml      OpenAPI 3 스펙 (HTTP)
//   /docs/async/index.html   WebSocket·STOMP·SSE 메시지 문서 (AsyncAPI 3) + async/index.yaml
//   /swagger-ui/index.html   위 스펙을 보여 주는 Swagger UI
//   /docs/openapi.html       같은 스펙의 정적 뷰어 (Redoc, kardx.openApiViewer)
//   /docs/postman-collection.json  Postman Collection (kardx.postmanCollection)
plugins {
    id("io.github.kardx-project") // 문서 파이프라인 + 라이브러리 의존성 (gradle-plugin, composite build)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot.v4)
    alias(libs.plugins.asciidoctor)
    alias(libs.plugins.epages.v4)
    id("restdocs.kotlin-sample") // ktlint·detekt, JUnit 플랫폼, 라이브러리 좌표 → 같은 빌드의 프로젝트 (build-logic)
}

// 라이브러리(annotations·core·kotest·boot)와 REST Docs·epages MockMvc 클라이언트는 플러그인이 넣는다 (docs/build.md)
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.v4.get()}"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.data:spring-data-commons") // Page·Pageable 추출 계약 (ExtractionContractTest)
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(libs.springdoc.webmvc.ui.v4)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // Kotest 스펙 문서화 (kotest/ 아래 스펙, ProjectConfig). 엔진과 Spring 확장은 프로젝트가 넣고, 버전(6.x)에 맞는 확장은 플러그인이 넣는다
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.extensions.spring)
}

// 경로·index 는 기본값이다. 아래는 기본값과 다른 것만 적는다.
kardx {
    language = "ko" // 생성 문서의 라이브러리 문구를 한국어로 (기본값 en). yml 이 아니라 여기서 정해야 플러그인 문구도 바뀐다
    asyncApi {
        title = "kardx 예제 메시지 API"
        version = "0.1"
        description = "WebSocket·STOMP·Server-Sent Events 메시지. 통과한 테스트가 실제로 주고받은 메시지에서 추출했다.\n\n" +
            "샘플 앱은 데모 토큰 `demo-token` 을 요구한다. 인증의 토큰 칸에 `demo-token` 을 넣고 연결해 보세요. " +
            "STOMP 는 CONNECT 헤더 `Authorization: Bearer demo-token` 으로 보내고, WebSocket·SSE 는 " +
            "'WebSocket·SSE 는 쿼리 파라미터로 보내기' 를 켜면 `?access_token=demo-token` 으로 보낸다."
        server("stomp", "stomp://localhost:8080/ws")
        server("websocket", "ws://localhost:8080")
        server("http", "http://localhost:8080")
        bearerAuth() // components.securitySchemes.bearerAuth. 뷰어의 연결 시험에 토큰 입력 칸이 생긴다
        fileName = "async/index" // build/api-docs/async/index.html. 시작 로그는 플러그인이 남긴 이 이름을 읽는다
    }
    // 아래는 기본으로 꺼져 있는 선택 기능이다 (docs/build.md)
    // KDoc·Javadoc → 설명 (실험 기능, 기본 꺼짐). 켜면 필드·enum·스키마·@param KDoc 을 쓰고, 컨트롤러 메서드 KDoc 을
    // OpenAPI summary·description 으로 쓰는 것(UserController·PaymentController·MemoController)은 구현 메모가 공개 문서로
    // 새지 않게 따로 켠다
    sourceDocs {
        enabled = true
        methodDocs = true
    }
    openApiViewer { enabled = true } // 기본 type 은 redoc (Boot 3 샘플은 scalar). 앱에서 /docs/openapi.html
    postmanCollection { enabled = true } // build/api-docs/postman-collection.json (epages postman)
    wireMock { enabled = true } // build/generated-snippets/wiremock/mappings, ./gradlew apiDocsStubsJar
    openApiCheck {
        // ./gradlew apiDocsCheck: docs/openapi/openapi3.yaml(커밋한 기준)과 비교. 갱신은 ./gradlew apiDocsBaseline
        failOn = "breaking" // 하위 호환을 깨는 변경만 실패, 나머지는 경고
    }
    // 테스트가 문서화하지 않은 공통 오류 응답. 응답이 없는 operation 에만 더한다 (401 은 인증이 필요한 operation 에만).
    // 본문은 오류 응답이 가장 많이 쓰는 스키마(ErrorResponse). 테스트로 확인하지 않은 내용이라 기본으로 꺼져 있다
    openApiCommonResponses.put(401, "토큰이 없거나 만료됨")
    openApiCommonResponses.put(500, "서버 오류")
    // 결제 API(PaymentApiDocsTest)의 openApi { oauth2("payments:write") } 가 쓰는 OAuth2 scheme.
    // 토큰 URL·scope 설명은 테스트가 모르므로 여기서 정한다. 다른 API 의 Bearer 토큰(bearerAuthJWT)과 함께 쓴다
    openApiSecurity {
        oauth2 {
            clientCredentials("https://auth.example.com/oauth/token")
            scope("payments:write", "결제 승인")
        }
    }
    // Spring Framework 7 API 버전(배송비 API). 버전을 정한 operation 에 x-api-version 과 필수 헤더 X-API-Version 을 적고,
    // splitSpecs 라 버전마다 스펙(openapi3-v1.0.yaml, openapi3-v1.1.yaml)도 만든다
    apiVersioning {
        header("X-API-Version")
        splitSpecs = true
    }
    // OpenAPI info·태그. 제목·버전·설명은 아래 openapi3 블록 그대로 두고 연락처·라이선스와 태그 순서·설명을 더한다.
    // tag() 로 적은 태그가 그 순서로 앞에 오고, 나머지 태그는 원래 순서대로 뒤에 온다
    openApiInfo {
        contact {
            name = "API 팀"
            email = "api@example.com"
        }
        license {
            name = "Apache 2.0"
            url = "https://www.apache.org/licenses/LICENSE-2.0"
        }
        tag("사용자 API")
        tag("상품 API") { description = "상품 조회·등록·검색" }
        tag("결제 API")
    }
    httpClient { enabled = true } // IntelliJ HTTP Client: build/api-docs/http-client/api.http + http-client.env.json
    // ./gradlew apiDocsExport: 스펙을 한 디렉터리로 모은다 (build 에는 붙지 않는다)
    export {
        directory = layout.buildDirectory.dir("exported-specs")
        asyncApi = true // build/exported-specs/spring-boot-4-asyncapi.yaml (OpenAPI 는 기본으로 들어간다)
    }
    // ./gradlew apiDocsUpload: 스펙을 API 레지스트리에 올리는 예 (url 을 정하지 않으면 실행할 때 실패한다)
    // upload {
    //     url = "https://registry.example.com/apis/{name}/{version}/{type}"
    //     headers.put("Authorization", providers.environmentVariable("API_REGISTRY_TOKEN").map { "Bearer $it" })
    // }
}

// format(yaml)·서버("/")는 플러그인 기본값이다
openapi3 {
    title = "kardx 예제 API"
    description = "통과한 테스트의 REST Docs 스니펫에서 추출한 HTTP API 스펙.\n\n" +
        "WebSocket·STOMP·Server-Sent Events 메시지는 [WebSocket·SSE 메시지 문서](/docs/async/index.html), " +
        "전체 문서는 [REST Docs](/docs/index.html) 에서 볼 수 있습니다."
    version = "0.1"
}
