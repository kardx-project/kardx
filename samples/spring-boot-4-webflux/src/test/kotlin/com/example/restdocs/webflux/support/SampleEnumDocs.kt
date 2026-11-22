package com.example.restdocs.webflux.support

import io.github.kardx.junit.EnumDocumentationExtension

/** 샘플 패키지의 `@EnumCode` Enum 으로 `code/enum-code.adoc` 을 만든다. */
class SampleEnumDocs : EnumDocumentationExtension(packageName = "com.example.restdocs.webflux", title = "코드 정의")
