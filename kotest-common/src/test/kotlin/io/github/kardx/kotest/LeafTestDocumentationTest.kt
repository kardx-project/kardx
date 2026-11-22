package io.github.kardx.kotest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import io.github.kardx.asciidoc.DocumentGeneratorFactory
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.extractor.responseFieldsOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.springframework.context.support.GenericApplicationContext
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.function.Supplier

class NamedByMapper(val userName: String)

private fun paths(): List<String> = responseFieldsOf<NamedByMapper>().map { it.descriptor.path }

class LeafTestDocumentationTest :
    BehaviorSpec({
        given("Spring 컨텍스트의 매퍼(SNAKE_CASE)를 쓰는 리프 테스트") {
            val context = GenericApplicationContext().apply {
                registerBean(
                    ObjectMapper::class.java,
                    Supplier { ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE) },
                )
                refresh()
            }
            val documentation = LeafTestDocumentation(
                sectionTitle = { it.names.joinToString(" - ") },
                snippetName = { it.names.joinToString("-") },
                documentGenerator = DocumentGeneratorFactory.DEFAULT,
                springContext = { context },
            )

            then("테스트 안에서는 앱 매퍼를 쓰고, 끝난 뒤 같은 스레드의 다음 테스트에는 남지 않는다") {
                val output = Files.createTempDirectory("leaf-mapper").toFile()

                RestDocsConfiguration.configure { this.output.snippetsDirectory = output.path }

                try {
                    Executors.newSingleThreadExecutor()
                        .asCoroutineDispatcher()
                        .use { thread ->
                            withContext(thread) {
                                documentation.intercept(
                                    leaf = true,
                                    specClass = LeafTestDocumentationTest::class.java,
                                    path = { listOf("매퍼") },
                                    outcome = { _: Unit? -> TestOutcome(errorOrFailure = false, success = true) },
                                ) {
                                    paths() shouldContainExactly listOf("user_name")
                                }

                                paths() shouldContainExactly listOf("userName")
                            }
                        }
                } finally {
                    RestDocsConfiguration.reset()
                    context.close()
                }
            }
        }

        given("테스트가 실패했는데 문서화 정리도 실패하면") {
            val documentation = LeafTestDocumentation(
                sectionTitle = { it.names.joinToString(" - ") },
                snippetName = { it.names.joinToString("-") },
                documentGenerator = DocumentGeneratorFactory.DEFAULT,
                springContext = { null },
            )

            then("테스트의 예외가 그대로 나가고 정리 예외는 suppressed 로 붙는다") {
                val output = Files.createTempDirectory("leaf-cleanup").toFile()

                RestDocsConfiguration.configure { this.output.snippetsDirectory = output.path }

                try {
                    val thrown = shouldThrow<AssertionError> {
                        documentation.intercept<Unit>(
                            leaf = true,
                            specClass = LeafTestDocumentationTest::class.java,
                            path = { listOf("정리 실패") },
                            outcome = { error("cleanup failed") },
                        ) {
                            throw AssertionError("body failed")
                        }
                    }

                    thrown.message shouldBe "body failed"
                    thrown.suppressed.map { it.message } shouldContainExactly listOf("cleanup failed")
                } finally {
                    RestDocsConfiguration.reset()
                }
            }
        }
    })
