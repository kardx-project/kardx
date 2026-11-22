package io.github.kardx.internal.output

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class OperationFileWriteTest :
    BehaviorSpec({
        given("없는 operation 디렉터리의 파일") {
            val directory = Files.createTempDirectory("operation").resolve("class-name/method-name")
            val target = directory.resolve("openapi-extras.json")

            `when`("두 번 쓰면") {
                target.writeOperationFile("{\"a\":1,\"long\":\"first content\"}")
                target.writeOperationFile("{\"a\":2}")

                then("디렉터리를 만들고 마지막 내용으로 덮어쓴다 (앞 내용이 남지 않는다)") {
                    Files.readString(target) shouldBe "{\"a\":2}"
                }

                then("임시 파일 없이 그 파일만 남는다") {
                    directory.listDirectoryEntries()
                        .map { it.name } shouldContainExactly listOf("openapi-extras.json")
                }
            }
        }
    })
