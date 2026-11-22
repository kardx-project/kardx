package io.github.kardx.internal.output

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class AtomicFileWriteTest :
    BehaviorSpec({
        given("없는 디렉터리의 파일") {
            val directory = Files.createTempDirectory("atomic").resolve("nested/dir")
            val target = directory.resolve("index.html")

            `when`("두 번 쓰면") {
                target.writeTextAtomically("첫 내용")
                target.writeTextAtomically("새 내용")

                then("디렉터리를 만들고 마지막 내용으로 바꾼다") {
                    Files.readString(target) shouldBe "새 내용"
                }

                then("임시 파일을 남기지 않는다") {
                    directory.listDirectoryEntries()
                        .map { it.name } shouldContainExactly listOf("index.html")
                }

                then("권한은 일반 파일 쓰기와 같다 (umask 를 따른다. 소유자 전용 0600 이 아니다)") {
                    if (Files.getFileAttributeView(target, PosixFileAttributeView::class.java) != null) {
                        val plain = Files.writeString(Files.createTempDirectory("plain").resolve("plain.html"), "")

                        Files.getPosixFilePermissions(target) shouldBe Files.getPosixFilePermissions(plain)
                    }
                }
            }
        }

        given("바꿔치기할 수 없는 대상 (비어 있지 않은 디렉터리)") {
            val directory = Files.createTempDirectory("atomic-fail")
            val target = Files.createDirectories(directory.resolve("index.html"))

            Files.writeString(target.resolve("keep.txt"), "")

            then("실패를 알리고 임시 파일을 남기지 않는다") {
                shouldThrow<UncheckedIOException> { target.writeTextAtomically("내용") }

                directory.listDirectoryEntries()
                    .map { it.name } shouldContainExactly listOf("index.html")
            }
        }
    })
