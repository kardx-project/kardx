# Module kardx-boot

Spring Boot 앱이 뜰 때 API 문서 주소(REST Docs, Swagger UI, OpenAPI 뷰어·스펙, AsyncAPI)를 INFO 로그로 남긴다.
의존성만 넣으면 `META-INF/spring.factories` 로 등록된다.

- `io.github.kardx.boot.ApiDocsStartupLogger`: 시작 로그. `kardx.startup-log.*` 로 항목·문구를 바꾸고,
  `kardx.startup-log.enabled=false` 로 끈다
