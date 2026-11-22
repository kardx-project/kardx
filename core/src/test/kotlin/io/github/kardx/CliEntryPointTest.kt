package io.github.kardx

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

/**
 * Gradle 플러그인(`AsyncApiTasks`, `IndexTasks`)이 `JavaExec.mainClass` 에 클래스 이름 문자열로 넣는 진입점.
 * 파일 이름·`@file:JvmName`·`main` 시그니처가 바뀌면 컴파일은 통과하고 플러그인 실행만 깨지므로 여기서 고정한다.
 */
private val CLI_CLASSES = listOf(
    "io.github.kardx.asyncapi.AsyncApiCli",
    "io.github.kardx.asciidoc.DocumentationIndexCli",
)

class CliEntryPointTest :
    BehaviorSpec({
        given("Gradle 플러그인이 실행하는 CLI 클래스") {
            then("public static void main(String[]) 이 있다") {
                CLI_CLASSES.forEach { className ->
                    withClue(className) {
                        val main = Class.forName(className)
                            .getMethod("main", Array<String>::class.java)

                        Modifier.isPublic(main.modifiers) shouldBe true
                        Modifier.isStatic(main.modifiers) shouldBe true
                        main.returnType shouldBe Void.TYPE
                    }
                }
            }
        }
    })
