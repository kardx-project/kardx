package io.github.kardx.kotest

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain

private const val KOTEST_FIVE = "io.kotest.core.test.TestResult"
private const val KOTEST_SIX = "io.kotest.engine.test.TestResult"

/** 주어진 클래스만 있는 것으로 보는 클래스패스. */
private fun classpathOf(vararg names: String): (String) -> Boolean = { it in names }

class KotestLineTest :
    BehaviorSpec({
        given("6.x 용 모듈") {
            `when`("클래스패스에 Kotest 5.x 가 있으면") {
                then("kotest5 아티팩트를 쓰라고 실패한다") {
                    shouldThrow<IllegalStateException> {
                        KotestLine.SIX.verify(classpathOf(KOTEST_FIVE))
                    }.message shouldContain
                        "Kotest 5.x is on the test classpath. Use kardx-kotest5"
                }
            }

            `when`("클래스패스에 Kotest 6.x 가 있으면") {
                then("통과한다") {
                    shouldNotThrowAny { KotestLine.SIX.verify(classpathOf(KOTEST_SIX)) }
                }
            }
        }

        given("5.x 용 모듈") {
            `when`("클래스패스에 Kotest 6.x 가 있으면") {
                then("kotest 아티팩트를 쓰라고 실패한다") {
                    shouldThrow<IllegalStateException> {
                        KotestLine.FIVE.verify(classpathOf(KOTEST_SIX))
                    }.message shouldContain
                        "Kotest 6.x is on the test classpath. Use kardx-kotest "
                }
            }
        }

        given("어느 판인지 알 수 없는 클래스패스") {
            then("건너뛴다") {
                shouldNotThrowAny { KotestLine.FIVE.verify(classpathOf()) }
                shouldNotThrowAny { KotestLine.SIX.verify(classpathOf()) }
            }
        }

        given("이 모듈을 실제 클래스패스에서 만들면") {
            then("판이 맞으므로 실패하지 않는다") {
                shouldNotThrowAny { RestDocsExtension() }
            }
        }
    })
