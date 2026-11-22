package io.kotest.provided

import io.github.kardx.kotest.RestDocsExtension
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension

/**
 * Kotest 프로젝트 설정. Kotest 6 이 이 이름(`io.kotest.provided.ProjectConfig`)으로 찾는다.
 *
 * Kotest 문서화 확장을 프로젝트 전체에 등록하는 예. 스펙에 확장이 없어도 `RestDocsExtension.currentTestProvider` 로
 * 문서화하고, Kotest Spring 확장을 붙인 스펙이면 그 컨텍스트의 `kardx.*` 을 읽는다
 * ([com.example.restdocs.kotest.ProjectExtensionSettingsSpec] 가 확인한다).
 * 스펙에도 등록한 [com.example.restdocs.kotest.UserSearchDocsSpec] 은 스펙의 확장이 문서화하고 문서도 그 확장이 쓴다
 * (이 확장은 그 스펙을 건너뛴다).
 */
class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(RestDocsExtension())
}
