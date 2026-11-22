package io.kotest.provided

import io.github.kardx.config.RestDocsConfiguration
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.listeners.AfterEachListener
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult

/**
 * Kotest 프로젝트 설정. Kotest 가 이 이름(`io.kotest.provided.ProjectConfig`)으로 찾는다.
 * 전역 설정([RestDocsConfiguration])은 JVM 하나에서 공유하므로, 매 테스트 뒤 되돌려 다음 테스트에 새지 않게 한다.
 */
class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(ResetRestDocsConfiguration)
}

/**
 * 매 테스트(leaf) 뒤 `configure` 값과 속성(yml) 값을 지운다. 테스트 안에서 실패해도 정리된다.
 * 한 테스트에서만 쓸 설정은 `RestDocsConfiguration.withSettings` 로 범위를 정하는 편이 낫다.
 */
private object ResetRestDocsConfiguration : AfterEachListener {
    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        RestDocsConfiguration.clearEnvironment()
        RestDocsConfiguration.reset()
    }
}
