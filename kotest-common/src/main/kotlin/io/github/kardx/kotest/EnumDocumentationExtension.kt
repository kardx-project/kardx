@file:OptIn(RestDocsExtensionApi::class)

package io.github.kardx.kotest

import io.github.kardx.annotation.EnumCode
import io.github.kardx.asciidoc.AsciiDocEnumDocumentFormat
import io.github.kardx.asciidoc.EnumDocumentFormat
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.lifecycle.EnumCodeDocumentation
import io.github.kardx.lifecycle.RestDocsExtensionApi
import io.github.kardx.lifecycle.SpringContextSettings
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import java.util.concurrent.ConcurrentHashMap

/**
 * [@EnumCode][EnumCode] 가 붙은 Enum 을 스캔해 코드 참조 문서를 만드는 Kotest 확장. JUnit 의
 * `io.github.kardx.junit.EnumDocumentationExtension` 과 같은 문서를 같은 코드로 만든다.
 *
 * 처음 실행되는 스펙 직전에 한 번 쓴다. 같은 패키지·출력 파일은 테스트 실행(JVM) 전체에서 한 번만 만들므로 여러 스펙에 붙이거나
 * 프로젝트 설정에 등록해도 된다. Kotest Spring 확장이 있으면 그 컨텍스트의 `kardx.*` 설정(`asciidoc-directory`,
 * `enum-description-property` 등)을 먼저 읽는다.
 *
 * ```kotlin
 * class ProjectConfig : AbstractProjectConfig() {
 *     override val extensions = listOf(
 *         EnumDocumentationExtension(packageName = "com.myapp", title = "코드 정의"),
 *     )
 * }
 * ```
 *
 * Kotest 5.x 는 `AbstractProjectConfig` 의 함수를 덮어쓴다: `override fun extensions() = listOf(EnumDocumentationExtension(...))`.
 *
 * @param packageName 스캔할 패키지. 하위 패키지도 스캔한다
 * @param fileName 출력 파일명 (확장자 제외)
 * @param title 문서 제목
 * @param outputDirectory 출력 디렉터리. `null` 이면 [io.github.kardx.config.OutputSettings.asciidocDirectory] 아래 `code`
 * @param format 문서 포맷. 기본값 AsciiDoc ([AsciiDocEnumDocumentFormat])
 */
public class EnumDocumentationExtension(
    private val packageName: String,
    private val fileName: String = "enum-code",
    private val title: String = "Enum Code Reference",
    private val outputDirectory: String? = null,
    private val format: EnumDocumentFormat = AsciiDocEnumDocumentFormat(),
) : BeforeSpecListener {

    override suspend fun beforeSpec(spec: Spec) {
        SpringContexts.current()?.let(SpringContextSettings::apply)

        val directory = EnumCodeDocumentation.directory(outputDirectory)

        if (WRITTEN.add("$packageName|$directory/$fileName")) {
            EnumCodeDocumentation.write(packageName, fileName, title, directory, format)
        }
    }

    private companion object {
        /** 이번 JVM 에서 쓴 (패키지, 출력 파일) */
        val WRITTEN: MutableSet<String> = ConcurrentHashMap.newKeySet()
    }
}
