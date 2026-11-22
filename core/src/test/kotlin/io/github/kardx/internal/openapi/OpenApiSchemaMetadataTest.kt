package io.github.kardx.internal.openapi

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.RestDocumentationContext
import org.springframework.restdocs.operation.Operation
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

/** 이름과 REST Docs 컨텍스트만 알려주는 최소 Operation. */
private fun operation(context: RestDocumentationContext): Operation =
    Proxy.newProxyInstance(Operation::class.java.classLoader, arrayOf(Operation::class.java)) { _, method, _ ->
        when (method.name) {
            "getName" -> "{class-name}/{method-name}"
            "getAttributes" -> mapOf(RestDocumentationContext::class.java.name to context)
            else -> null
        }
    } as Operation

class OpenApiSchemaMetadataTest :
    BehaviorSpec({
        given("스키마 이름의 출처") {
            val output = Files.createTempDirectory("schemas").toFile()
            val documentation = ManualRestDocumentation(output.path)

            documentation.beforeTest(OpenApiSchemaMetadataTest::class.java, "getItem")
            OpenApiSchemaMetadata(
                request = null,
                response = SchemaOrigin("Item", automatic = true, type = "com.shop.Item", candidates = listOf("Item")),
            ).document(operation(documentation.beforeOperation()))

            documentation.afterTest()

            then("resource.json 과 같은 스니펫 디렉터리에 남는다") {
                val file = File(output, "open-api-schema-metadata-test/get-item/${OpenApiSchemaMetadata.FILE_NAME}")

                file.isFile shouldBe true
                file.readText() shouldContain "\"automatic\":true"
                file.readText() shouldContain "\"type\":\"com.shop.Item\""
            }
        }
    })
