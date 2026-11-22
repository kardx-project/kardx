// 모듈 공통 설정은 build-logic 의 컨벤션 플러그인(restdocs.*)에 있다. 여기에는 루트에만 해당하는 것만 둔다.
plugins {
    // 외부 플러그인을 루트 클래스로더에 한 번만 올린다. 모듈이 따로 올리면 Kotlin 플러그인이 여러 번 로드된다.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.binary.compatibility.validator) apply false
    // 두 샘플의 공통 소스가 같은지 check 에서 확인한다 (아래 checkSampleSync)
    id("restdocs.sample-sync")
    // 루트에 build·clean·publishToMavenLocal 태스크를 만든다. 아래에서 gradle-plugin(included build)의 같은 태스크에 연결한다
    base
    `maven-publish`
}

allprojects {
    group = property("group") as String
    version = property("version") as String

    repositories {
        mavenCentral()
    }
}

// 샘플은 Boot 3 / 4 에서 같은 코드가 도는 것을 보이려고 소스를 두 벌 둔다. 아래 파일만 달라도 된다.
// WebFlux 샘플(samples/spring-boot-4-webflux)은 다른 앱이라 비교하지 않는다.
tasks.named<SampleSourcesSyncCheck>("checkSampleSync") {
    firstSources.set(layout.projectDirectory.dir("samples/spring-boot-3/src"))
    secondSources.set(layout.projectDirectory.dir("samples/spring-boot-4/src"))
    allowedDifferences.addAll(
        // AsyncAPI 문서 위치: Boot 3 은 asyncapi.html, Boot 4 는 async/index.html (각 샘플 build 파일의 asyncApi.fileName).
        // application.yml 은 이제 파일 이름을 적지 않으므로(Gradle 플러그인이 남긴 값) 두 샘플이 같다
        "docs/asciidoc/index-template.adoc",
        // Jackson 2(ObjectMapper) / Jackson 3(JsonMapper)
        "test/kotlin/com/example/restdocs/extraction/ExtractionContractTest.kt",
    )
    // Jackson 2 의 속성 순서를 Jackson 3 기본값처럼 고정하는 설정. Jackson 3 에는 없는 키가 있어 Boot 3 에만 둔다
    onlyInFirst.add("main/resources/config/application.yml")
    onlyInSecond.addAll(
        "test/kotlin/com/example/restdocs/extraction/StreamingContractTest.kt",
        "test/kotlin/com/example/restdocs/extraction/WrapperAndSecurityTest.kt",
        // RestTestClient(Spring Framework 7) 문서화 예제와 그 예제가 문서화하는 엔드포인트. Boot 3 에는 RestTestClient 가 없다
        "main/kotlin/com/example/restdocs/coupon/CouponListController.kt",
        "test/kotlin/com/example/restdocs/resttestclient/CouponListDocsTest.kt",
        // API 버전(Spring Framework 7 `@GetMapping(version = ...)`, ApiVersionConfigurer) 예제. Boot 3 에는 없는 기능이다
        "main/kotlin/com/example/restdocs/shipping/ApiVersionConfig.kt",
        "main/kotlin/com/example/restdocs/shipping/ShippingFeeController.kt",
        "main/kotlin/com/example/restdocs/shipping/ShippingFeeResponse.kt",
        "test/kotlin/com/example/restdocs/apiversion/ShippingFeeDocsTest.kt",
    )
    // JUnit 문서화 테스트를 Kotest 로 그대로 옮긴 스펙. Boot 4 샘플에만 둔다 (UserSearchDocsSpec 등 기존 Kotest 스펙은 양쪽에 있다)
    onlyInSecond.addAll(
        listOf(
            "UserControllerSpec.kt",
            "auth/AuthApiDocsSpec.kt",
            "chat/ChatMessageDocsSpec.kt",
            "coupon/CouponApiDocsSpec.kt",
            "echo/EchoWebSocketDocsSpec.kt",
            "edge/EdgeCaseDocsSpec.kt",
            "notification/NotificationApiDocsSpec.kt",
            "payment/PaymentApiDocsSpec.kt",
            "product/ProductApiDocsSpec.kt",
            "showcase/DslShowcaseDocsSpec.kt",
            "support/MirrorDocsConfig.kt",
        ).map { "test/kotlin/com/example/restdocs/kotest/$it" },
    )
}

// 검사 스크립트(scripts/*.py)를 돌릴 파이썬. Windows 처럼 python3 이 없으면 `-Prestdocs.python=py` 로 바꾼다
val python = providers.gradleProperty("restdocs.python")
    .orElse("python3")

// 영어 문서(README.md, CONTRIBUTING.md, docs/)와 한국어 문서(README.ko.md, CONTRIBUTING.ko.md, docs/ko/)의
// 구조(제목·코드 블록·표·링크)가 같은지 check 에서 확인한다.
// 규칙과 의도한 차이 표시는 scripts/check-docs-pairs.py 의 머리말에 있다. 문서가 바뀌지 않았으면 다시 실행하지 않는다
val checkDocsPairs by tasks.registering(Exec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "영어·한국어 문서 쌍의 구조가 같은지 확인한다"

    val script = layout.projectDirectory.file("scripts/check-docs-pairs.py")
    val result = layout.buildDirectory.file("docs-pairs/result.txt")

    inputs.file(script)
        .withPropertyName("script")
    inputs.files(layout.projectDirectory.files("README.md", "README.ko.md", "CONTRIBUTING.md", "CONTRIBUTING.ko.md"))
        .withPropertyName("readmes")
    inputs.files(layout.projectDirectory.dir("docs").asFileTree.matching { include("**/*.md") })
        .withPropertyName("docs")
    outputs.file(result)
        .withPropertyName("result")

    workingDir = layout.projectDirectory.asFile
    commandLine(python.get(), script.asFile.path, "--root", layout.projectDirectory.asFile.path)

    val resultFile = result.map { it.asFile }

    doLast {
        resultFile.get().writeText("docs pairs in step\n")
    }
}

// core, kotest, kotest5(각각 공유 소스 kotest-common 포함), Gradle·Maven 플러그인, tooling 의 패키지 의존에 순환이 없는지 check 에서 확인한다.
// 검사 단위와 규칙은 scripts/check-package-cycles.py 의 머리말에 있다. 소스가 바뀌지 않았으면 다시 실행하지 않는다
val checkPackageCycles by tasks.registering(Exec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "core, kotest, kotest5, Gradle·Maven 플러그인, tooling 의 패키지 의존에 순환이 없는지 확인한다"

    val script = layout.projectDirectory.file("scripts/check-package-cycles.py")
    val result = layout.buildDirectory.file("package-cycles/result.txt")
    val sourceDirectories = listOf(
        "core/src/main/kotlin",
        "kotest/src/main/kotlin",
        "kotest5/src/main/kotlin",
        "kotest-common/src/main/kotlin",
        "gradle-plugin/src/main/java",
        "tooling/src/main/java",
        "maven-plugin/src/main/java",
    )

    inputs.file(script)
        .withPropertyName("script")
    val sources = sourceDirectories.map { directory ->
        layout.projectDirectory.dir(directory)
            .asFileTree
            .matching { include("**/*.kt", "**/*.java") }
    }

    inputs.files(sources)
        .withPropertyName("sources")
    outputs.file(result)
        .withPropertyName("result")

    workingDir = layout.projectDirectory.asFile
    // 디렉터리를 주지 않으면 스크립트의 기본 검사 단위(위 소스 디렉터리)를 쓴다
    commandLine(python.get(), script.asFile.path)

    val resultFile = result.map { it.asFile }

    doLast {
        resultFile.get().writeText("no package cycles\n")
    }
}

// 문제 해결 문서(영어·한국어)의 오류 코드 표가 RestDocsErrorCode 와 같은지 check 에서 확인한다.
// 코드마다 앵커(#kdx-011)가 있어야 오류 메시지의 문서 주소가 맞는다. 고치려면 python3 scripts/update-error-codes.py
val checkErrorCodes by tasks.registering(Exec::class) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "문제 해결 문서의 오류 코드 표가 RestDocsErrorCode 와 같은지 확인한다"

    val script = layout.projectDirectory.file("scripts/update-error-codes.py")
    val result = layout.buildDirectory.file("error-codes/result.txt")

    inputs.file(script)
        .withPropertyName("script")
    inputs.files(
        layout.projectDirectory.files(
            "core/src/main/kotlin/io/github/kardx/diagnostics/RestDocsErrorCode.kt",
            "docs/troubleshooting.md",
            "docs/ko/troubleshooting.md",
        ),
    ).withPropertyName("sources")
    outputs.file(result)
        .withPropertyName("result")

    workingDir = layout.projectDirectory.asFile
    commandLine(python.get(), script.asFile.path, "--check")

    val resultFile = result.map { it.asFile }

    doLast {
        resultFile.get().writeText("error code tables in step\n")
    }
}

tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) {
    dependsOn(checkDocsPairs, checkPackageCycles, checkErrorCodes)
}

// Gradle 플러그인과 tooling 은 composite build 라서 루트의 build·check·clean·배포 태스크에 따로 연결한다.
//
// clean 은 clean 만 요청했을 때만 연결한다. gradle-plugin 의 jar 는 샘플이 플러그인으로 쓰려고 설정 단계에서 먼저 만들어진다.
// 같은 실행에서 그 빌드의 clean 과 build 를 함께 돌리면, clean 은 jar 를 쓰는 태스크가 끝나길 기다리고
// 그 태스크는 clean 뒤에 오도록 정렬돼 Gradle 이 교착한다("Unable to make progress running work").
// `./gradlew clean build` 에서 gradle-plugin 은 지우지 않고 증분 빌드한다. 다시 만들려면 `./gradlew clean` 을 따로 실행한다.
//
// 판단은 요청한 태스크 이름 문자열만 본다. 축약형(`./gradlew cl`, `./gradlew cle`)이나 `-x` 와 섞인 이름은 `clean` 과
// 같지 않으므로 gradle-plugin 의 clean 을 연결하지 않는다 (그 빌드만 지워지지 않을 뿐 실패하지는 않는다).
val onlyCleanRequested = gradle.startParameter.taskNames.all { it == "clean" || it == ":clean" }
val includedBuildTasks = if (onlyCleanRequested) {
    setOf("publishToMavenLocal", "build", "check", "clean")
} else {
    setOf("publishToMavenLocal", "build", "check")
}

tasks.matching { it.name in includedBuildTasks }
    .configureEach {
        dependsOn(gradle.includedBuild("gradle-plugin").task(":$name"))
        dependsOn(gradle.includedBuild("tooling").task(":$name"))
    }

// Maven Central 업로드 묶음: 모든 배포 모듈(루트의 restdocs.published 모듈, tooling, gradle-plugin)을 build/staging-deploy 에
// 배포한다. 서명 키(signingInMemoryKey)를 주면 서명까지 한다. 그다음 scripts/central-upload.sh 가 묶어 올린다 (release.yml).
// 예전 실행의 파일이 남아 있으면 같이 묶이므로 버전을 바꿨다면 먼저 build/staging-deploy 를 지운다.
val publishToStaging by tasks.registering {
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    description = "Publishes all modules, including tooling and the Gradle plugin, to build/staging-deploy."
    dependsOn(gradle.includedBuild("tooling").task(":publishAllPublicationsToStagingRepository"))
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishAllPublicationsToStagingRepository"))
}

subprojects {
    pluginManager.withPlugin("restdocs.published") {
        val publishModule = tasks.named("publishAllPublicationsToStagingRepository")

        publishToStaging.configure { dependsOn(publishModule) }
    }
}
