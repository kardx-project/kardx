package io.github.kardx.internal.reflect

/**
 * 클래스를 초기화하지 않고 로드한다. 클래스가 없거나(`ClassNotFoundException`) 의존 클래스가 빠져 링크할 수 없으면
 * (`LinkageError`) 실패 결과를 돌려준다. 실패 원인을 예외 메시지에 담아야 하는 곳에서 쓴다.
 *
 * @param loader 기본값은 [defaultClassLoader] (앱 클래스패스를 먼저 본다)
 */
internal fun loadClassCatching(name: String, loader: ClassLoader = defaultClassLoader()): Result<Class<*>> = try {
    Result.success(Class.forName(name, false, loader))
} catch (e: ClassNotFoundException) {
    Result.failure(e)
} catch (e: LinkageError) {
    Result.failure(e)
}

/** [loadClassCatching] 과 같고, 로드할 수 없으면 null. 선택 의존(Jackson 2/3, epages 등)을 확인할 때 쓴다. */
internal fun loadClassOrNull(name: String, loader: ClassLoader = defaultClassLoader()): Class<*>? =
    loadClassCatching(name, loader).getOrNull()

/** 스레드 컨텍스트 클래스 로더(앱 클래스패스). 없으면 이 라이브러리를 로드한 클래스 로더. */
internal fun defaultClassLoader(): ClassLoader =
    Thread.currentThread().contextClassLoader ?: LibraryClassLoader::class.java.classLoader

/** 라이브러리 클래스 로더를 얻기 위한 표식. */
private object LibraryClassLoader
