package io.github.kardx.junit

import io.github.kardx.annotation.EnumCode
import io.github.kardx.asciidoc.AsciiDocEnumDocumentFormat
import io.github.kardx.asciidoc.EnumDocumentFormat
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.internal.spring.SpringSettingsLoader
import io.github.kardx.lifecycle.EnumCodeDocumentation
import io.github.kardx.lifecycle.RestDocsExtensionApi
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * [@EnumCode][EnumCode] 가 붙은 Enum 을 스캔해 코드 참조 문서를 만드는 JUnit 5 확장.
 *
 * 표 내용과 기본값은 [EnumCode] 를 본다. Spring 테스트면 yml 의 `kardx.*` 설정을 먼저 읽는다.
 *
 * 스캔할 패키지를 지정해야 하므로 상속하거나 `@RegisterExtension` 으로 등록한다. 같은 패키지·출력 파일은 테스트 실행 전체에서
 * 한 번만 만든다. 여러 테스트 클래스에 붙여도, 병렬로 실행해도 스캔과 파일 쓰기는 한 번이다. JUnit 콜백은 바꿀 수 없다.
 * 생성자 인자는 Kotest `EnumDocumentationExtension` 과 같다.
 *
 * ```kotlin
 * class MyEnumDocs : EnumDocumentationExtension(packageName = "com.myapp", title = "코드 정의")
 *
 * @ExtendWith(MyEnumDocs::class, AutoRestDocumentationExtension::class)
 * abstract class ApiDocsTest
 * ```
 *
 * @param packageName 스캔할 패키지. 하위 패키지도 스캔한다
 * @param fileName 출력 파일명 (확장자 제외)
 * @param title 문서 제목
 * @param outputDirectory 출력 디렉터리. `null` 이면 [io.github.kardx.config.OutputSettings.asciidocDirectory] 아래 `code`
 * @param format 문서 포맷. 기본값 AsciiDoc ([AsciiDocEnumDocumentFormat])
 */
@OptIn(RestDocsExtensionApi::class)
public open class EnumDocumentationExtension(
    private val packageName: String,
    private val fileName: String = "enum-code",
    private val title: String = "Enum Code Reference",
    private val outputDirectory: String? = null,
    private val format: EnumDocumentFormat = AsciiDocEnumDocumentFormat(),
) : BeforeAllCallback {

    final override fun beforeAll(context: ExtensionContext) {
        // Spring 테스트면 yml 의 kardx.* (enum-description-property, asciidoc-directory 등)를 먼저 읽는다
        SpringSettingsLoader.load(context)

        val directory = EnumCodeDocumentation.directory(outputDirectory)

        @Suppress("DEPRECATION") // getOrComputeIfAbsent: JUnit 5/6 모두에서 동작하는 API
        context.root.getStore(NAMESPACE)
            .getOrComputeIfAbsent("$packageName|$directory/$fileName") {
                EnumCodeDocumentation.write(packageName, fileName, title, directory, format)
                true
            }
    }

    private companion object {
        val NAMESPACE: ExtensionContext.Namespace =
            ExtensionContext.Namespace.create(EnumDocumentationExtension::class.java)
    }
}
