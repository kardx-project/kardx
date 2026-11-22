package io.github.kardx.internal.sourcedocs

import java.io.File

/** 테스트 리소스 `source-docs/[name]` 의 절대 경로. */
fun sourceDocsFixture(name: String): String = File(
    requireNotNull(SourceDocs::class.java.getResource("/source-docs/$name")).toURI(),
).absolutePath

/** 색인 경로 시스템 속성을 [path] 로 바꾸고 JVM 당 한 번 읽은 색인을 버린다. null 이면 속성을 지운다. */
fun useSourceDocsIndex(path: String?) {
    if (path == null) {
        System.clearProperty(SourceDocs.PROPERTY)
    } else {
        System.setProperty(SourceDocs.PROPERTY, path)
    }

    SourceDocs.reset()
}
