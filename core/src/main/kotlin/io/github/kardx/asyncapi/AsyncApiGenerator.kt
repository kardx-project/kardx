package io.github.kardx.asyncapi

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.asyncapi.AsyncApiEntry
import io.github.kardx.internal.asyncapi.AsyncApiMetadata
import io.github.kardx.internal.asyncapi.AsyncApiRecordCodec
import io.github.kardx.internal.asyncapi.AsyncApiViewer
import io.github.kardx.internal.asyncapi.EventStreamRecord
import io.github.kardx.internal.asyncapi.MessageRecord
import io.github.kardx.internal.asyncapi.YamlWriter
import io.github.kardx.internal.jackson.deserializeJson
import io.github.kardx.internal.output.writeTextAtomically
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

/**
 * 테스트가 남긴 메시지·이벤트 스트림 메타데이터(`*.asyncapi.json`)로 AsyncAPI 3.0 문서를 만든다.
 *
 * 메타데이터는 [io.github.kardx.websocket.MessageDocumentation] (WebSocket / STOMP) 과
 * `andDocument { events(...) }` (Server-Sent Events) 가 자동으로 남긴다.
 *
 * | 라이브러리 | AsyncAPI |
 * |---|---|
 * | 목적지(destination) / SSE 경로 | channel |
 * | SEND (클라이언트 → 서버) | operation `action: receive` (서버 관점) |
 * | SUBSCRIBE, SSE 이벤트 (서버 → 클라이언트) | operation `action: send` |
 * | 헤더 디스크립터 | message headers (JSON Schema) |
 * | 필드 디스크립터 | message payload (JSON Schema). 제약조건은 설명에 붙는다 |
 * | 실제 페이로드 | message examples |
 * | 테스트 클래스 `@DisplayName` | operation tag |
 * | 테스트 메서드 `@DisplayName` | operation summary |
 * | `name` 파라미터 (없으면 `send-app-chat-send` 처럼 방향+목적지), SSE 이벤트 이름 | message 이름 |
 *
 * Gradle 에서는 코드 없이 `io.github.kardx.asyncapi.AsyncApiCli` 를 `JavaExec` 로 실행하면 된다.
 */
public object AsyncApiGenerator {

    /**
     * [snippetsDirectory] 에 남은 메시지·이벤트 기록으로 AsyncAPI 3.0 문서 [output] 을 만든다 (있으면 [viewer] 도).
     *
     * @param snippetsDirectory REST Docs 스니펫 디렉터리 (보통 `build/generated-snippets`)
     * @param output 만들 AsyncAPI 파일 (`.yaml`)
     * @param info 문서의 `info`(title, version, description), `servers`, `securitySchemes`
     * @param viewer 함께 만들 HTML 뷰어 파일 (문서 + Try it out). null 이면 만들지 않는다. 스펙을 페이지 안에 넣는다
     * @return 문서에 들어간 메시지 수. 문서화한 메시지·이벤트가 없으면 0 이고 파일을 만들지 않는다 (빈 문서는 쓸모가 없다)
     */
    public fun generate(snippetsDirectory: Path, output: Path, info: AsyncApiInfo, viewer: Path? = null): Int {
        val entries = readEntries(snippetsDirectory)

        if (entries.none { it.record is EventStreamRecord || (it.record as? MessageRecord)?.isConnect == false }) {
            Files.deleteIfExists(output)
            viewer?.let(Files::deleteIfExists)

            return 0
        }

        val document = AsyncApiDocumentBuilder(info).build(entries)
        output.writeTextAtomically(YamlWriter.write(document))
        viewer?.writeTextAtomically(AsyncApiViewer.html(info.title, document))

        return ((document["components"] as? Map<*, *>)?.get("messages") as? Map<*, *>)?.size ?: 0
    }

    private fun readEntries(directory: Path): List<AsyncApiEntry> {
        if (!Files.isDirectory(directory)) return emptyList()

        val mapper = RestDocsConfiguration.objectMapper()

        return Files.walk(directory).use { paths ->
            paths.asSequence()
                .filter { it.isRegularFile() && it.name.endsWith(AsyncApiMetadata.FILE_SUFFIX) }
                .sortedBy { directory.relativize(it).toString() }
                .mapNotNull {
                    AsyncApiRecordCodec.decode(deserializeJson(mapper, it.readText(), Map::class.java))
                }
                .toList()
        }
    }
}
