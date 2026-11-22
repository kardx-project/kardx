package io.github.kardx.kotest

import io.github.kardx.diagnostics.RestDocsErrorCode

/**
 * 확장 모듈이 컴파일된 Kotest 판과 그 판에만 있는 클래스. `kotest`(6.x)·`kotest5`(5.x) 는 같은 패키지·같은 클래스를 담아서
 * 클래스패스의 Kotest 판과 어긋나면 첫 테스트에서 `NoSuchMethodError` 로 실패한다. [verify] 가 그 전에 알아보기 쉽게 실패시킨다.
 */
internal enum class KotestLine(private val label: String, private val artifactId: String, private val marker: String) {
    FIVE("5.x", "kardx-kotest5", "io.kotest.core.test.TestResult"),
    SIX("6.x", "kardx-kotest", "io.kotest.engine.test.TestResult"),
    ;

    /**
     * 클래스패스의 Kotest 가 이 판인지 본다. 이 판의 표시 클래스가 없고 다른 판의 것이 있으면 [IllegalStateException].
     * 어느 판인지 알 수 없으면(둘 다 없음) 건너뛴다.
     */
    fun verify(classPresent: (String) -> Boolean = ::isPresent) {
        if (classPresent(marker)) {
            return
        }

        val actual = entries.firstOrNull { it != this && classPresent(it.marker) } ?: return

        error(
            RestDocsErrorCode.KOTEST_VERSION_MISMATCH.message(
                "$artifactId is built for Kotest $label, but Kotest ${actual.label} is on the test classpath. " +
                    "Use ${actual.artifactId} instead (same package and API), and keep only one of the two.",
            ),
        )
    }

    private companion object {
        fun isPresent(name: String): Boolean = runCatching {
            Class.forName(name, false, KotestLine::class.java.classLoader)
        }.isSuccess
    }
}
