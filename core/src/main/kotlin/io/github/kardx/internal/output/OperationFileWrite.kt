package io.github.kardx.internal.output

import io.github.kardx.diagnostics.RestDocsErrorCode
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * 문서화 한 번(operation)에 딸린 UTF-8 텍스트 파일을 바로 쓴다. 상위 디렉터리가 없으면 만든다. 실패하면 [UncheckedIOException].
 *
 * [writeTextAtomically] 와 달리 임시 파일·바꿔치기를 하지 않는다 (요청당 약 0.15 ms 차이). operation 디렉터리
 * (`<class-name>/<method-name>/`)의 파일은 그 테스트 하나만 쓰고, Gradle 태스크가 테스트 태스크가 끝난 뒤에야 읽으므로
 * 쓰는 도중의 파일을 다른 쪽이 읽을 일이 없다. REST Docs 스니펫과 epages `resource.json` 도 같은 디렉터리에 이렇게 쓴다.
 * 여러 JVM·테스트가 같이 쓰는 파일(문서, index, 전역 헤더 파일 등)은 [writeTextAtomically] 를 쓴다.
 */
internal fun Path.writeOperationFile(text: String) {
    val target = toAbsolutePath()

    try {
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    } catch (e: IOException) {
        throw UncheckedIOException(
            RestDocsErrorCode.WRITE_FAILED.message("Failed to write ${target.fileName} to ${target.parent}"),
            e,
        )
    }
}

/** [Path.writeOperationFile] 의 [File] 판. */
internal fun File.writeOperationFile(text: String): Unit = toPath().writeOperationFile(text)
