package com.example.restdocs.webflux.support

import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.webtestclient.restDocsWebTestClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 문서화 테스트의 공통 기본 클래스 (WebTestClient).
 *
 * - 공통 설정(민감 헤더 마스킹, 오류 응답 공통 스키마)은 `application-docs.yml` 의 `kardx.*` 에 있다
 * - 앱 컨텍스트에 서버 없이 묶은 REST Docs 용 WebTestClient 를 만든다 (`restDocsWebTestClient`)
 * - `apiDocsIndex` 태스크가 이 클래스의 하위 클래스를 찾아 index.adoc 에 링크한다
 */
@ExtendWith(SampleEnumDocs::class, AutoRestDocumentationExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
abstract class RestDocsTestSupport {

    protected lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUpRestDocs(context: ApplicationContext, provider: RestDocumentationContextProvider) {
        webTestClient = restDocsWebTestClient(context, provider)
    }
}
