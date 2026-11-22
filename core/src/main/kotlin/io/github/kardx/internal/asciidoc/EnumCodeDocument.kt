package io.github.kardx.internal.asciidoc

import io.github.kardx.annotation.EnumCode
import io.github.kardx.asciidoc.EnumDocumentFormat
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.diagnostics.RestDocsErrorCode
import io.github.kardx.internal.introspection.deprecationOf
import io.github.kardx.internal.output.writeTextAtomically
import io.github.kardx.internal.reflect.defaultClassLoader
import io.github.kardx.internal.reflect.enumJsonValues
import io.github.kardx.internal.reflect.enumPropertyOf
import io.github.kardx.internal.reflect.loadClassCatching
import io.github.kardx.internal.reflect.scanClassMetadata
import io.github.kardx.internal.sourcedocs.SourceDocs
import java.io.File

/**
 * `@EnumCode` 가 붙은 Enum 을 스캔해 코드 참조 문서를 만든다.
 *
 * `classpath*:` 패턴으로 패키지와 하위 패키지를 스캔하므로 여러 모듈(jar)에 흩어진 Enum 도 찾는다.
 *
 * - 코드: [EnumCode.codeProperty] 값. 비어 있으면 JSON 값 ([enumJsonValues])
 * - 설명: [EnumCode.descriptionProperty] 값. 비어 있으면 설정 `extraction.enumDescriptionProperty` → `description` →
 *   상수의 KDoc·Javadoc 설명([SourceDocs]) → 상수 이름
 * - 직접 지정한 프로퍼티가 없으면 [IllegalArgumentException]
 * - `@Deprecated` 상수는 제외한다
 */
internal class EnumCodeDocument(private val format: EnumDocumentFormat, private val directory: String) {
    /**
     * @param packageName 스캔할 패키지 (예: `com.myapp.domain`). 하위 패키지도 스캔한다
     * @param fileName 출력 파일명 (확장자 제외)
     * @param title 문서 제목
     */
    fun write(packageName: String, fileName: String, title: String) {
        require(packageName.isNotBlank()) { "packageName must not be blank" }

        val titles = mutableSetOf<String>()
        val tables = findAnnotatedEnums(packageName).map { enumClass ->
            // 같은 제목의 Enum 이 둘이면 앵커가 겹치므로 뒤의 것에 클래스 이름을 붙인다. 접미사도 제목 규칙을 따른다
            val title = sanitizeTitle(enumClass.getAnnotation(EnumCode::class.java).value)

            table(enumClass, if (titles.add(title)) title else "$title$TITLE_SEPARATOR${enumClass.simpleName}")
        }

        val blocks = listOf(format.header(title, packageName, indexLink())) + tables + format.footer()
        val document = blocks.filter { it.isNotEmpty() }
            .joinToString(separator = "\n\n", postfix = "\n")

        File(directory, "$fileName.${format.fileExtension}").writeTextAtomically(document)
    }

    /**
     * 이 문서 위치에서 본 index 링크. 상대 경로면 AsciiDoc 출력 디렉터리까지 올라가는 `../` 를 붙인다 (`code/` → `../index.html`).
     * 링크를 껐거나 문서가 AsciiDoc 출력 디렉터리 밖이면 null. 절대 경로·URL 은 그대로 쓴다.
     */
    private fun indexLink(): String? {
        val target = RestDocsConfiguration.current.document.indexLink?.takeIf { it.isNotBlank() } ?: return null

        if (target.startsWith("/") || target.contains("://")) return target

        return levelsBelowAsciidocDirectory()?.let { "../".repeat(it) + target }
    }

    /** 이 문서 디렉터리가 AsciiDoc 출력 디렉터리에서 몇 단계 아래인지. 그 밖이면 null. */
    private fun levelsBelowAsciidocDirectory(): Int? {
        val root = RestDocsConfiguration.asciidocDirectory()
            .toPath()
            .toAbsolutePath()
            .normalize()
        val here = File(directory).toPath()
            .toAbsolutePath()
            .normalize()

        if (!here.startsWith(root)) return null

        return root.relativize(here)
            .count { it.toString().isNotEmpty() }
    }

    private fun table(enumClass: Class<*>, title: String): String {
        val annotation = enumClass.getAnnotation(EnumCode::class.java)
        val code = codeOf(enumClass, annotation.codeProperty)
        val description = descriptionOf(enumClass, annotation.descriptionProperty)
        val rows = enumClass.enumConstants.orEmpty()
            .filterIsInstance<Enum<*>>()
            .filterNot { isDeprecated(enumClass, it) }
            .map { constant -> format.tableRow(code(constant), description(constant)) }

        val blocks = listOf(format.tableHeader(title, enumClass.simpleName)) + rows + format.tableFooter()

        return blocks.filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    /** 코드 열 값. 지정하지 않으면 JSON 에 실제로 나가는 값. */
    private fun codeOf(enumClass: Class<*>, property: String): (Enum<*>) -> String {
        if (property.isEmpty()) {
            val values = enumJsonValues(enumClass)

            return { constant -> values[constant.ordinal] }
        }

        return valueOrName(requireProperty(enumClass, "codeProperty", property))
    }

    /** 설명 열 값. 지정하지 않으면 설정 `extraction.enumDescriptionProperty` → `description` → KDoc·Javadoc → 상수 이름. */
    private fun descriptionOf(enumClass: Class<*>, property: String): (Enum<*>) -> String {
        if (property.isNotEmpty()) {
            return valueOrName(requireProperty(enumClass, "descriptionProperty", property))
        }

        val fallback = listOf(
            RestDocsConfiguration.current.extraction.enumDescriptionProperty,
            DEFAULT_DESCRIPTION_PROPERTY,
        )
            .firstNotNullOfOrNull { enumPropertyOf(enumClass, it) }
        val sourceDocsEnabled = RestDocsConfiguration.current.descriptions.sourceDocs

        return { constant ->
            fallback?.invoke(constant)
                ?: SourceDocs.enumConstantDescription(sourceDocsEnabled, enumClass, constant.name)
                ?: constant.name
        }
    }

    private fun requireProperty(enumClass: Class<*>, attribute: String, property: String): (Enum<*>) -> String? =
        requireNotNull(enumPropertyOf(enumClass, property)) {
            RestDocsErrorCode.ENUM_CODE_PROPERTY_MISSING.message(
                "@EnumCode($attribute = \"$property\") on ${enumClass.name}: the enum has no property '$property'. " +
                    "Check the name, or leave $attribute empty to use the default.",
            )
        }

    private fun valueOrName(property: (Enum<*>) -> String?): (Enum<*>) -> String =
        { constant -> property(constant) ?: constant.name }

    private fun findAnnotatedEnums(packageName: String): List<Class<*>> {
        val loader = defaultClassLoader()
        val annotation = EnumCode::class.java.name

        return scanClassMetadata(packageName, loader).filter { it.annotationMetadata.hasAnnotation(annotation) }
            .map { reader ->
                val className = reader.classMetadata.className

                loadClassCatching(className, loader).getOrElse {
                    throw IllegalStateException(
                        RestDocsErrorCode.ENUM_CLASS_NOT_LOADED.message("Enum class $className could not be loaded."),
                        it,
                    )
                }
            }
            .filter { it.isEnum }
            .distinct()
            .sortedBy { it.getAnnotation(EnumCode::class.java).value }
    }

    private fun isDeprecated(enumClass: Class<*>, constant: Enum<*>): Boolean =
        deprecationOf(enumClass.getField(constant.name).annotations.toList()) != null

    /**
     * AsciiDoc 제목·앵커에 쓸 수 없는 문자를 바꾼다. `/` 는 앞뒤에 공백을 둔 `-` 로 바꿔 읽을 수 있게 둔다
     * (`입금/출금` → `입금 - 출금`, 앵커 `입금_-_출금`). 이어진 공백은 하나로 줄인다.
     */
    private fun sanitizeTitle(value: String): String = value.replace(">", "-")
        .replace("/", TITLE_SEPARATOR)
        .replace('(', '-')
        .replace(')', '-')
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        const val DEFAULT_DESCRIPTION_PROPERTY = "description"
        const val TITLE_SEPARATOR = " - "
        val WHITESPACE = Regex("\\s+")
    }
}
