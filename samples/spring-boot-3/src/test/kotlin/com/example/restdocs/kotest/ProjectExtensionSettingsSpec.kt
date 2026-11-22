package com.example.restdocs.kotest

import com.example.restdocs.common.RestResponse
import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * 프로젝트 설정에 등록한 Kotest 문서화 확장([io.kotest.provided.ProjectConfig])도 Kotest Spring 확장의 컨텍스트에서
 * `kardx.*` 을 읽는지 확인한다. 스펙에는 문서화 확장이 없다.
 *
 * `index-link-text` 는 이 스펙의 컨텍스트에만 있는 값이라, 다른 테스트가 먼저 읽은 yml 값으로 통과할 수 없다.
 * 문서화 호출이 없으므로 문서는 만들지 않는다.
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@TestPropertySource(properties = ["kardx.index-link-text=$LINK_TEXT"])
class ProjectExtensionSettingsSpec :
    FunSpec({
        test("프로젝트 전체에 등록한 확장이 Spring 컨텍스트의 kardx.* 을 적용한다") {
            val settings = RestDocsConfiguration.current

            assertThat(settings.document.indexLinkText).isEqualTo(LINK_TEXT)
            assertThat(settings.extraction.responseWrapper?.wrapperClass).isEqualTo(RestResponse::class)
            assertThat(settings.document.commonIncludeFiles).containsExactly("_landing.adoc")
        }
    })

private const val LINK_TEXT = "프로젝트 전체 등록 확인"
