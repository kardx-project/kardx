package io.github.kardx.noservlet

import io.github.kardx.websocket.MessageDocumentation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile

/** 이 클래스패스에 없어야 하는 MockMvc·서블릿 쪽 클래스. 있으면 아래 검사가 의미 없다. */
private val SERVLET_CLASSES = listOf(
    "jakarta.servlet.Filter",
    "org.springframework.web.servlet.DispatcherServlet",
    "org.springframework.restdocs.mockmvc.MockMvcRestDocumentation",
)

/** 검사한 클래스 수의 하한. 클래스 목록을 잘못 읽어 아무것도 검사하지 않고 통과하는 것을 막는다. */
private const val MIN_CHECKED_CLASSES = 50

/**
 * MockMvc 사용자만 로드하는 패키지. 이 밖의 공개 클래스는 MockMvc·서블릿 없이 로드돼야 한다.
 * `resttestclient` 는 Spring Framework 7 의 RestTestClient(서블릿 쪽 spring-test)로 컴파일한 진입점이라 여기서는 로드되지 않는다.
 */
private val MOCK_MVC_PACKAGES = listOf(
    "io/github/kardx/mockmvc/",
    "io/github/kardx/resttestclient/",
    "io/github/kardx/sse/",
    "io/github/kardx/internal/",
)

/** core 의 클래스 파일 이름 (`io/github/kardx/...class`). jar 로 받든 디렉터리로 받든 같다. */
private fun coreClassFiles(): List<String> {
    val location = File(
        MessageDocumentation::class.java.protectionDomain.codeSource.location
            .toURI(),
    )

    if (location.isDirectory) {
        return location.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".class") }
            .map { it.relativeTo(location).invariantSeparatorsPath }
            .toList()
    }

    return ZipFile(location).use { jar ->
        jar.entries()
            .asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") && !it.startsWith("META-INF/") }
            .toList()
    }
}

private fun className(classFile: String): String = classFile.removeSuffix(".class")
    .replace('/', '.')

/**
 * 공개 클래스를 초기화하고 선언(필드·메서드·생성자)의 타입을 모두 풀어 선언 수를 돌려준다. 공개 클래스가 아니면 0.
 * 클래스패스에 없는 타입이 시그니처·필드·정적 초기화에 있으면 [LinkageError] 로 실패한다.
 */
private fun resolveDeclarations(name: String): Result<Int> = runCatching {
    val loader = MessageDocumentation::class.java.classLoader
    val type = Class.forName(name, false, loader)

    if (!Modifier.isPublic(type.modifiers)) {
        return@runCatching 0
    }

    Class.forName(name, true, loader)
        .let { it.declaredFields.size + it.declaredMethods.size + it.declaredConstructors.size }
}

class NoServletClasspathTest :
    BehaviorSpec({
        given("spring-restdocs-mockmvc·spring-webmvc·서블릿 API 가 없는 테스트 클래스패스") {
            then("MockMvc·서블릿 클래스가 정말 없다") {
                SERVLET_CLASSES.forEach { name ->
                    shouldThrow<ClassNotFoundException> { Class.forName(name) }
                }
            }

            `when`("MockMvc 전용 패키지 밖의 공개 클래스를 모두 로드하면") {
                val classes = coreClassFiles().filter { file -> MOCK_MVC_PACKAGES.none(file::startsWith) }
                    .map(::className)
                val results = classes.associateWith(::resolveDeclarations)
                val failures = results.mapNotNull { (name, result) -> result.exceptionOrNull()?.let { "$name: $it" } }

                then("NoClassDefFoundError 없이 초기화되고 시그니처가 풀린다") {
                    failures shouldBe emptyList()
                    classes.size shouldBeGreaterThan MIN_CHECKED_CLASSES
                    results.values.sumOf { it.getOrDefault(0) } shouldBeGreaterThan 0
                }
            }
        }
    })
