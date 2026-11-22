package io.github.kardx.lifecycle

import io.github.kardx.annotation.EnumCode
import io.github.kardx.asciidoc.EnumDocumentFormat
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.internal.asciidoc.EnumCodeDocument
import java.io.File

/**
 * [@EnumCode][EnumCode] 가 붙은 Enum 을 스캔해 코드 참조 문서를 쓴다. JUnit·Kotest 의 Enum 문서 확장이 함께 쓴다.
 * 표 내용과 기본값은 [EnumCode] 를 본다.
 */
@RestDocsExtensionApi
public object EnumCodeDocumentation {
    /**
     * 문서를 쓸 디렉터리. [outputDirectory] 가 null 이면 [io.github.kardx.config.OutputSettings.asciidocDirectory] 아래 `code`.
     * yml 설정(`kardx.asciidoc-directory`)을 읽은 뒤에 부른다.
     */
    public fun directory(outputDirectory: String?): String =
        outputDirectory ?: File(RestDocsConfiguration.asciidocDirectory(), "code").path

    /**
     * [packageName] 과 하위 패키지의 `@EnumCode` Enum 으로 `<directory>/<fileName>.<format.fileExtension>` 을 쓴다.
     * 같은 문서를 여러 번 쓰지 않도록 부르는 쪽이 (패키지, 디렉터리, 파일) 별로 한 번만 부른다.
     *
     * @param directory [directory] 로 정한 출력 디렉터리
     */
    public fun write(
        packageName: String,
        fileName: String,
        title: String,
        directory: String,
        format: EnumDocumentFormat,
    ) {
        EnumCodeDocument(format, directory).write(packageName, fileName, title)
    }
}
