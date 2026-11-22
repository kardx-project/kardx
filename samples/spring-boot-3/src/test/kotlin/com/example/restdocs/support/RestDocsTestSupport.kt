package com.example.restdocs.support

import io.github.kardx.asciidoc.DocumentGenerator
import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.junit.EnumDocumentationExtension
import io.github.kardx.mockmvc.restDocsMockMvc
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.context.WebApplicationContext

/** 샘플 패키지의 `@EnumCode` Enum 으로 `code/enum-code.adoc` 을 만든다. */
class SampleEnumDocs : EnumDocumentationExtension(packageName = "com.example.restdocs", title = "코드 정의")

/** 문서 생성기 커스터마이징 예: 목차를 2단계까지만 보여 준다. */
class SampleDocumentGenerator : DocumentGenerator() {
    override fun config(): Config = Config(tocLevels = 2)
}

/** 문서를 [SampleDocumentGenerator] 로 쓰는 JUnit 문서화 확장. 생성기는 생성자 인자로 준다. */
fun sampleDocsExtension(): AutoRestDocumentationExtension =
    AutoRestDocumentationExtension(documentGenerator = DocumentGeneratorFactory { SampleDocumentGenerator() })

/**
 * 문서화 테스트의 공통 기본 클래스.
 *
 * - 공통 설정(응답 래퍼, 문서 상단 include, 마스킹)은 `application-docs.yml` 의 `kardx.*` 에 있다
 * - 코드로 정하는 설정(오류 응답 공통 스키마 등)은 [DocsConfig] 에 있다. yml 로도 같은 설정을 할 수 있다
 * - REST Docs 용 MockMvc 를 만든다
 * - 테스트 메서드·`@BeforeEach` 에서 한 설정은 그 테스트가 끝나면 확장이 되돌린다 (`@AfterEach` 에서 `reset()` 할 필요 없음)
 * - `apiDocsIndex` 태스크가 이 클래스의 하위 클래스를 찾아 index.adoc 에 링크한다
 */
@ExtendWith(SampleEnumDocs::class)
@SpringBootTest
@ActiveProfiles("docs")
abstract class RestDocsTestSupport {

    protected lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUpRestDocs(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        DocsConfig.apply() // 코드 설정 (yml 은 application-docs.yml)
        mockMvc = restDocsMockMvc(context, provider)
    }

    companion object {
        /** 하위 클래스에도 적용된다. 생성자 인자를 주려면 `@ExtendWith` 대신 `@RegisterExtension` 으로 등록한다. */
        @JvmField
        @RegisterExtension
        val restDocs: AutoRestDocumentationExtension = sampleDocsExtension()
    }
}
