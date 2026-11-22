// 코드 품질: ktlint(형식) + detekt(정적 분석). Kotlin 소스가 있는 모든 모듈(라이브러리·샘플)에 적용한다.
plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

ktlint {
    version.set(versionCatalog.requiredVersion("ktlint-engine"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

// detekt 1.23 은 Kotlin 2.0.21(카탈로그 detekt-kotlin)로 빌드됐다.
// 의존성 관리 플러그인이 detekt 클래스패스의 Kotlin 을 올리지 않게 고정한다.
val detektKotlinVersion = versionCatalog.requiredVersion("detekt-kotlin")

configurations.matching { it.name == "detekt" }
    .configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion(detektKotlinVersion)
            }
        }
    }
