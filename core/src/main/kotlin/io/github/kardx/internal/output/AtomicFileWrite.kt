package io.github.kardx.internal.output

import io.github.kardx.diagnostics.RestDocsErrorCode
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * UTF-8 텍스트를 파일에 쓴다. 상위 디렉터리가 없으면 만든다.
 * 같은 디렉터리의 임시 파일에 쓴 뒤 원자적으로 바꿔치기하므로, 여러 테스트 JVM(`maxParallelForks`)이 같은 파일을
 * 동시에 써도 읽는 쪽이 반쯤 쓰인 파일을 보지 않는다. 실패하면 [UncheckedIOException] 을 던진다.
 *
 * 임시 파일은 `Files.createTempFile` 대신 일반 파일로 만든다. `createTempFile` 은 권한이 `rw-------` 라
 * 바꿔치기한 결과물(배포하는 HTML·YAML 등)도 소유자만 읽을 수 있게 되기 때문이다. 일반 파일은 umask 를 따른다.
 * 쓰기나 바꿔치기가 실패하면 임시 파일을 지워 스니펫 디렉터리에 남기지 않는다.
 */
internal fun Path.writeTextAtomically(text: String) {
    val target = toAbsolutePath()
    val directory = target.parent
    val temp = directory.resolve(".${target.fileName}.${UUID.randomUUID()}.tmp")

    try {
        Files.createDirectories(directory)
        Files.writeString(temp, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (e: IOException) {
        throw UncheckedIOException(
            RestDocsErrorCode.WRITE_FAILED.message("Failed to write ${target.fileName} to $directory"),
            e,
        )
    } finally {
        deleteLeftover(temp)
    }
}

/** 바꿔치기하지 못하고 남은 임시 파일을 지운다. 성공했으면 이미 옮겨져 없다. */
private fun deleteLeftover(temp: Path) {
    try {
        Files.deleteIfExists(temp)
    } catch (ignored: IOException) {
        // 정리 실패가 쓰기 결과(성공 또는 이미 던진 예외)를 가리지 않게 삼킨다. 남은 파일은 다음 cleanBeforeTest 가 지운다
    }
}

/** [Path.writeTextAtomically] 의 [File] 판. */
internal fun File.writeTextAtomically(text: String): Unit = toPath().writeTextAtomically(text)
