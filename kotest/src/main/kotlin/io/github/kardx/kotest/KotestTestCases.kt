package io.github.kardx.kotest

import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.core.test.parents

// Kotest 6.x 의 TestCase 를 문서화 입력으로 읽는다. 5.x 모듈(kotest5)에 같은 이름의 5.x 판이 있다.

/** 문서화 대상인 리프 테스트(`then`, `it`, `test` 등)인지. 컨테이너(`given`, `describe`, `context` 등)는 아니다. */
internal val TestCase.isLeaf: Boolean
    get() = type == TestType.Test

/** 바깥 컨테이너에서 이 테스트까지의 이름. 스타일 접두사(`Given: ` 등)와 포커스·제외 표시는 뺀 이름이다. */
internal fun TestCase.documentationPath(): List<String> = (parents() + this).map { it.name.name }

/**
 * 이 테스트를 [extension] 이 문서화하는지. 스펙이 자기 [RestDocsExtension] 을 등록했으면(DSL `extension(RestDocsExtension(...))`
 * 이나 `override val extensions` 속성) 그 확장이 문서화하고, 프로젝트 설정에 등록한 확장은 그 스펙을 건너뛴다. 그래서 스펙의 `sectionTitle`·`snippetName`·
 * `documentGenerator` 가 프로젝트 설정의 것보다 우선한다. 스펙에 등록한 확장이 없으면 어느 확장이든 문서화한다.
 */
internal fun TestCase.isDocumentedBy(extension: RestDocsExtension): Boolean {
    // 6.x 의 extensions() 는 DSL 로 등록한 것만 돌려주고, `extensions` 속성은 따로 읽어야 한다
    val own = (spec.extensions + spec.extensions()).filterIsInstance<RestDocsExtension>()

    return own.isEmpty() || own.any { it === extension }
}
