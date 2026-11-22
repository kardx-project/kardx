package io.github.kardx.asciidoc

import io.github.kardx.config.DocumentSectionOrder
import io.github.kardx.internal.output.SnippetPaths
import io.github.kardx.internal.output.TestMethodListExtractor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.ClassOrderer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestClassOrder
import org.junit.jupiter.api.TestMethodOrder
import java.io.File
import java.nio.file.Files

/** 순서 어노테이션이 없는 테스트 클래스. */
@Disabled("문서 순서 픽스처")
class UnorderedDocTest {
    @Test
    @DisplayName("나 생성")
    fun alpha() = Unit

    @Test
    @DisplayName("가 조회")
    fun beta() = Unit
}

@Disabled("문서 순서 픽스처")
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestClassOrder(ClassOrderer.OrderAnnotation::class)
class OrderedDocTest {
    @Test
    @Order(2)
    fun alpha() = Unit

    @Test
    @Order(1)
    fun beta() = Unit

    @Test
    fun gamma() = Unit

    @Nested
    @Order(2)
    inner class First {
        @Test
        fun one() = Unit
    }

    @Nested
    @Order(1)
    inner class Second {
        @Test
        fun two() = Unit
    }
}

@Disabled("문서 순서 픽스처")
@TestMethodOrder(MethodOrderer.DisplayName::class)
class DisplayNameOrderedDocTest {
    @Test
    @DisplayName("다 삭제")
    fun alpha() = Unit

    @Test
    @DisplayName("가 조회")
    fun beta() = Unit
}

/** 사용자 정의 orderer 는 알 수 없으므로 기본 순서를 쓴다. */
@Disabled("문서 순서 픽스처")
@TestMethodOrder(MethodOrderer.Random::class)
class RandomOrderedDocTest {
    @Test
    fun beta() = Unit

    @Test
    fun alpha() = Unit
}

@DisplayName("나 문서")
class IndexOrderB

@Order(1)
@DisplayName("다 문서")
class IndexOrderC

@DisplayName("가 문서")
class IndexOrderA

private fun names(testClass: Class<*>, order: DocumentSectionOrder = DocumentSectionOrder.METHOD_NAME) =
    TestMethodListExtractor.extract(testClass, order)
        .map { it.method.name }

class SectionOrderTest :
    BehaviorSpec({
        given("순서 어노테이션이 없는 테스트 클래스") {
            then("기본은 메서드 이름순이다") {
                names(UnorderedDocTest::class.java) shouldContainExactly listOf("alpha", "beta")
            }

            then("설정으로 표시 이름순을 고를 수 있다") {
                names(UnorderedDocTest::class.java, DocumentSectionOrder.DISPLAY_NAME) shouldContainExactly
                    listOf("beta", "alpha")
            }
        }

        given("@TestMethodOrder(OrderAnnotation) 와 @TestClassOrder(OrderAnnotation)") {
            then("@Order 순서이고, @Order 가 없으면 뒤에 오며, @Nested 클래스도 @Order 순서다") {
                names(OrderedDocTest::class.java) shouldContainExactly listOf("beta", "alpha", "gamma", "two", "one")
            }
        }

        given("@TestMethodOrder(DisplayName)") {
            then("표시 이름순이다") {
                names(DisplayNameOrderedDocTest::class.java) shouldContainExactly listOf("beta", "alpha")
            }
        }

        given("JUnit 이 정하는 순서를 알 수 없는 orderer (Random)") {
            then("설정한 기본 순서를 쓴다") {
                names(RandomOrderedDocTest::class.java) shouldContainExactly listOf("alpha", "beta")
            }
        }

        given("index 의 테스트 클래스 순서") {
            then("클래스의 @Order 가 먼저이고, 나머지는 표시 이름순이다") {
                val dir = Files.createTempDirectory("index-order").toFile()
                val template = File(dir, "index-template.adoc").apply { writeText(DocumentationIndex.MARKER + "\n") }
                val classes = listOf(IndexOrderB::class.java, IndexOrderC::class.java, IndexOrderA::class.java)

                classes.forEach { File(dir, "${SnippetPaths.camelCaseToDash(it.simpleName)}.adoc").writeText("= 문서\n") }

                val output = File(dir, "index.adoc")

                DocumentationIndex.write(template, output, classes)

                Regex("=== (.+)").findAll(output.readText())
                    .map { it.groupValues[1] }
                    .toList() shouldBe listOf("다 문서", "가 문서", "나 문서")
            }
        }
    })
