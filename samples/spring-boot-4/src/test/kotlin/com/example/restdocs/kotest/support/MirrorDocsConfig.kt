package com.example.restdocs.kotest.support

import com.example.restdocs.support.DocsConfig
import com.example.restdocs.support.SampleDocumentGenerator
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.kotest.RestDocsExtension

/**
 * JUnit 문서화 테스트를 Kotest 로 그대로 옮긴 스펙(`kotest` 아래 `*Spec`, [com.example.restdocs.kotest.UserSearchDocsSpec]
 * 제외)의 설정. 스펙은 [restDocsExtension] 을 등록하고 `beforeEach` 에서 [apply] 를 부른다. 테스트가 끝나면 확장이 설정을
 * 되돌린다.
 *
 * 같은 API 를 JUnit 테스트가 이미 문서화하므로, 이 스펙들의 문서는 앱에 싣는 산출물에 넣지 않는다.
 * 스니펫(`build/generated-snippets/<spec>/`)과 AsciiDoc 문서([ASCIIDOC_DIRECTORY])는 그대로 만들어 확인할 수 있다.
 *
 * | 설정 | 빼는 것 |
 * |---|---|
 * | `output.asciidocDirectory` | index·HTML (둘 다 `build/generated-asciidoc` 의 문서만 읽는다) |
 * | `openApi.enabled = false` | OpenAPI 의 같은 operation 에 붙는 두 번째 예시, Postman 항목 |
 * | `asyncApi.enabled = false` | AsyncAPI 메시지 예시 |
 * | `output.wireMockStubs = false` | 같은 요청의 두 번째 WireMock 매핑 |
 */
object MirrorDocsConfig {
    /** 이 스펙들의 AsciiDoc 문서 디렉터리 (샘플 프로젝트 디렉터리 기준) */
    const val ASCIIDOC_DIRECTORY = "build/generated-asciidoc-kotest"

    /**
     * JUnit 문서([com.example.restdocs.support.sampleDocsExtension])와 같은 생성기(목차 2단계)로 문서를 쓰는 확장.
     * 프로젝트 설정([io.kotest.provided.ProjectConfig])에도 확장이 있지만 스펙에 등록한 이 확장이 문서화한다.
     */
    fun restDocsExtension(): RestDocsExtension = RestDocsExtension(documentGenerator = { SampleDocumentGenerator() })

    fun apply() {
        DocsConfig.apply()
        RestDocsConfiguration.configure {
            output.asciidocDirectory = ASCIIDOC_DIRECTORY
            openApi.enabled = false
            asyncApi.enabled = false
            output.wireMockStubs = false
        }
    }
}
