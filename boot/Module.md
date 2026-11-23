# Module kardx-boot

Logs the API documentation addresses (REST Docs, Swagger UI, OpenAPI viewer and spec, AsyncAPI) at INFO when a Spring Boot app starts.
Adding the dependency is enough; it is registered through `META-INF/spring.factories`.

- `io.github.kardx.boot.ApiDocsStartupLogger`: the startup log. Change its entries and text with `kardx.startup-log.*`, and
  turn it off with `kardx.startup-log.enabled=false`
