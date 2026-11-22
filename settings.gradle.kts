pluginManagement {
    // 모듈 공통 빌드 설정(컨벤션 플러그인 restdocs.*). 각 모듈이 plugins { id("restdocs.kotlin-library") } 처럼 쓴다.
    includeBuild("build-logic")
    // Gradle 플러그인(io.github.kardx-project)을 샘플이 바로 쓰도록 composite build 로 포함한다.
    includeBuild("gradle-plugin")
}

rootProject.name = "kardx"

// 빌드 도구와 무관한 후처리 로직과 명령줄(kardx-tooling). gradle-plugin 도 같은 빌드를 포함한다.
// 여기서도 포함해야 루트의 Maven 플러그인 모듈이 좌표 io.github.kardx-project:kardx-tooling 을 이 빌드로 바꿔 쓴다.
includeBuild("tooling")

// 배포 모듈
include("annotations") // kardx-annotations
include("core") // kardx
include("boot") // kardx-boot (앱 시작 시 문서 주소 로그)
include("kotest") // kardx-kotest (Kotest 6.x 스펙 문서화 확장)
include("kotest5") // kardx-kotest5 (Kotest 5.x 판. 공유 소스는 kotest-common/)
include("bom") // kardx-bom
include("maven-plugin") // kardx-maven-plugin (Maven 목표 api-docs, check, baseline, source-docs)

// 샘플 애플리케이션 (배포하지 않음)
include("samples:spring-boot-3")
include("samples:spring-boot-4")
include("samples:spring-boot-4-webflux") // WebFlux + WebTestClient (서블릿 스택 없음)
