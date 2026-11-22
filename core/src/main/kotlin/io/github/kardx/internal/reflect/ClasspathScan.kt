package io.github.kardx.internal.reflect

import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory

/**
 * [packageName] 과 하위 패키지의 클래스 파일을 로드하지 않고 메타데이터로 읽는다.
 * `classpath*:` 패턴이라 여러 모듈(jar)에 흩어진 클래스도 찾는다. 클래스를 로드할 때는 같은 [loader] 를 쓴다.
 *
 * @param loader 기본값은 [defaultClassLoader] (앱 클래스패스를 먼저 본다)
 */
internal fun scanClassMetadata(packageName: String, loader: ClassLoader = defaultClassLoader()): List<MetadataReader> {
    val readers = SimpleMetadataReaderFactory(loader)

    return PathMatchingResourcePatternResolver(loader)
        .getResources("classpath*:${packageName.replace('.', '/')}/**/*.class")
        .map { readers.getMetadataReader(it) }
}
