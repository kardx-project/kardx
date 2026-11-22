import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/** 루트 빌드의 버전 카탈로그(gradle/libs.versions.toml). 컨벤션 플러그인 스크립트에는 `libs` 접근자가 생기지 않는다. */
internal val Project.versionCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>()
        .named("libs")

/** 카탈로그 `[versions]` 의 값. 없으면 빌드 설정 오류다. */
internal fun VersionCatalog.requiredVersion(alias: String): String = findVersion(alias)
    .orElseThrow { IllegalStateException("gradle/libs.versions.toml 에 버전 '$alias' 가 없습니다") }
    .requiredVersion
