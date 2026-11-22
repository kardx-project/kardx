// 루트 빌드의 모듈들이 plugins { id("restdocs.xxx") } 로 쓰는 컨벤션 플러그인.
// src/main/kotlin/restdocs.*.gradle.kts 파일 이름이 플러그인 id 다.
plugins {
    `kotlin-dsl`
}

dependencies {
    // 컨벤션 플러그인이 적용하는 외부 플러그인. 루트 build 파일도 같은 버전을 apply false 로 올린다.
    implementation(pluginArtifact(libs.plugins.kotlin.jvm))
    implementation(pluginArtifact(libs.plugins.ktlint))
    implementation(pluginArtifact(libs.plugins.detekt))
    implementation(pluginArtifact(libs.plugins.binary.compatibility.validator))
    implementation(pluginArtifact(libs.plugins.dokka.javadoc))
}

/** 버전 카탈로그의 플러그인 → 플러그인 마커 좌표 (`<id>:<id>.gradle.plugin:<version>`). */
fun pluginArtifact(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }
