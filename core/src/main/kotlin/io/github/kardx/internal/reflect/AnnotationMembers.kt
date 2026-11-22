package io.github.kardx.internal.reflect

/**
 * 어노테이션 멤버 값을 이름으로 읽는다. 멤버가 없거나 호출에 실패하면 null.
 * Jackson·JUnit 처럼 컴파일 의존하지 않는(클래스패스에 없을 수도 있는) 어노테이션을 리플렉션으로 읽을 때 쓴다.
 *
 * public 이 아닌 어노테이션(Kotlin `private annotation class`, Java package-private `@interface`)의 멤버는
 * 다른 패키지에서 호출할 수 없으므로 [java.lang.reflect.Method.trySetAccessible] 로 연다.
 * `getMethod` 는 호출마다 새 `Method` 를 돌려주므로 다른 사용처의 접근 검사에는 영향이 없다.
 */
internal fun Annotation.memberValue(name: String = "value"): Any? = runCatching {
    val method = annotationClass.java.getMethod(name)

    method.trySetAccessible()
    method.invoke(this)
}.getOrNull()

/**
 * 어노테이션 타입의 이름이 [name](FQCN)인지. 클래스패스에 없을 수도 있는 어노테이션(Jackson, JUnit, Spring)을
 * 컴파일 의존 없이 찾을 때 쓴다.
 */
internal fun Annotation.hasName(name: String): Boolean = annotationClass.java.name == name
