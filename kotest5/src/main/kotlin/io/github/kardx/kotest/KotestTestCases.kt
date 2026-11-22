package io.github.kardx.kotest

import io.kotest.common.ExperimentalKotest
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.core.test.parents

// Kotest 5.x 의 TestCase 를 문서화 입력으로 읽는다. 6.x 모듈(kotest)에 같은 이름의 6.x 판이 있다.

/**
 * 문서화 대상인 리프 테스트인지. `then`, `it`, `test` 같은 [TestType.Test] 와, 데이터 테스트(`withData`)의 행처럼 실행 중에
 * 더한 [TestType.Dynamic] 이다. 5.x 의 `Dynamic` 은 컨테이너일 수도 있어 미리 알 수 없으므로, Kotest Spring 확장(5.x)과 같이
 * 리프로 본다. 그 안에 다시 테스트를 두면(`withData` 중첩) 안쪽 테스트는 바깥 행의 섹션에 함께 들어간다.
 */
@OptIn(ExperimentalKotest::class)
internal val TestCase.isLeaf: Boolean
    get() = type == TestType.Test || type == TestType.Dynamic

/** 바깥 컨테이너에서 이 테스트까지의 이름. 5.x 는 `TestName.testName` 이 접두사·포커스 표시를 뺀 이름이다. */
internal fun TestCase.documentationPath(): List<String> = (parents() + this).map { it.name.testName }

/**
 * 이 테스트를 [extension] 이 문서화하는지. 스펙이 자기 [RestDocsExtension] 을 등록했으면(`extension(RestDocsExtension(...))`)
 * 그 확장이 문서화하고, 프로젝트 설정에 등록한 확장은 그 스펙을 건너뛴다. 그래서 스펙의 `sectionTitle`·`snippetName`·
 * `documentGenerator` 가 프로젝트 설정의 것보다 우선한다. 스펙에 등록한 확장이 없으면 어느 확장이든 문서화한다.
 */
internal fun TestCase.isDocumentedBy(extension: RestDocsExtension): Boolean {
    val own = (spec.registeredExtensions() + spec.extensions()).filterIsInstance<RestDocsExtension>()

    return own.isEmpty() || own.any { it === extension }
}
