[English](../development.md) | **한국어**

# 개발

[문서 목록](README.md)

<!-- toc -->
**목차**

- [배포](#배포)
<!-- /toc -->

이 저장소를 고치거나 검증할 때 쓰는 방법입니다. 라이브러리 사용자에게는 필요 없습니다. 이슈, 커밋 메시지, PR 은 [기여 안내](../../CONTRIBUTING.ko.md)([English](../../CONTRIBUTING.md))에 있습니다.

```
kardx/
├── annotations/      kardx-annotations (의존성 없음)
├── core/             kardx (Spring REST Docs 3.0 API 로 컴파일, 4.0 과 바이너리 호환)
├── boot/             kardx-boot (앱 시작 시 문서 주소 로그. Boot 3 로 컴파일, 4 와 바이너리 호환)
├── kotest/           kardx-kotest (Kotest 6.x 스펙 문서화 확장. Kotest 는 compileOnly)
├── kotest5/          kardx-kotest5 (같은 확장의 Kotest 5.x 판. 같은 패키지·공개 API)
├── kotest-common/    kotest·kotest5 가 함께 컴파일하는 공유 소스와 테스트 (Gradle 프로젝트 아님, 배포 안 함)
├── bom/              kardx-bom
├── gradle-plugin/    io.github.kardx-project Gradle 플러그인 (composite build, Java)
├── tooling/          kardx-tooling (테스트 다음 단계와 CLI, Gradle API 없음. included build, Java)
├── maven-plugin/     kardx-maven-plugin (tooling 위의 Maven 목표, Java)
├── samples/
│   ├── spring-boot-3/    Boot 3.5 + epages 0.19 + springdoc 2.8 (JUnit 테스트 + Kotest 스펙 하나)
│   ├── spring-boot-4/    Boot 4.1 + epages 0.20 + springdoc 3.2 (JUnit 테스트 + Kotest 스펙 하나)
│   ├── spring-boot-4-webflux/  Boot 4.1 WebFlux + WebTestClient (서블릿 스택 없음. JUnit 테스트 + Kotest 스펙 하나)
│   └── maven-boot-4/     Maven 플러그인을 쓰는 Boot 4.1 Maven 프로젝트 (Gradle 빌드에 들어가지 않음)
├── build-logic/      모듈 공통 빌드 설정 (restdocs.* 컨벤션 플러그인)
├── config/detekt/    detekt 규칙
├── gradle/           버전 카탈로그(libs.versions.toml)와 wrapper
├── docs/             사용자 문서
└── scripts/          문서 산출물·실행 앱 검증, 문서 목차 갱신 스크립트
```

core 의 패키지별 책임과 공개 여부, 계층, 리소스, 계약 테스트는 [아키텍처 문서의 core](architecture.md#core) 에 있습니다. JUnit 확장과 Kotest 확장이 함께 쓰는 문서화 수명주기는 core 의 `io.github.kardx.lifecycle`(opt-in `@RestDocsExtensionApi`)에 있고, 두 확장에서 로직을 복제하지 않습니다([아키텍처 문서의 kotest](architecture.md#kotest)). 사용자가 import 하지 않는 구현은 `io.github.kardx.internal.*`(Gradle 플러그인은 `io.github.kardx.gradle.internal.*`, tooling 은 `io.github.kardx.tooling.internal.*`)에 둡니다. Gradle 플러그인에는 연결 코드만 있고 `tooling` 의 단계(`internal.steps`)를 부릅니다. Maven 플러그인(`io.github.kardx.maven`)과 CLI 는 `internal.pipeline` 을 거쳐 같은 코드를 부릅니다([아키텍처 문서의 tooling](architecture.md#tooling)).

라이브러리나 플러그인 코드를 고친 뒤에는 아래 순서로 확인합니다. 뒤 단계일수록 느리고, 앞 단계가 잡지 못하는 것을 잡습니다. 검증 스크립트는 항목마다 `PASS`/`FAIL` 을 출력하고, 하나라도 실패하면 종료 코드 1 로 끝납니다(CI 도 종료 코드로 판정).

```bash
# 1. 고친 모듈의 테스트
./gradlew :core:test                # 또는 :kotest:test :kotest5:test, Gradle 플러그인은 ./gradlew -p gradle-plugin test
./gradlew -p tooling build          # tooling: 테스트와 apiCheck (javap). Maven 플러그인은 ./gradlew :maven-plugin:test

# 2. 전체 빌드: 빌드, 테스트, ktlint, detekt, apiCheck, checkSampleSync, checkDocsPairs, checkPackageCycles, checkErrorCodes (Gradle 플러그인·tooling 빌드 포함. check 도 그 빌드까지 돈다)
#    샘플도 하위 프로젝트라서 샘플의 bootJar 와 그에 딸린 apiDocs 까지 만든다
./gradlew build

# 3. 문서 산출물 검증 (스니펫·AsciiDoc·HTML·OpenAPI·AsyncAPI·뷰어·Postman·WireMock. 항목 수는 샘플마다 다르고 결과 끝에 출력된다)
./gradlew :samples:spring-boot-4:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-4
./gradlew :samples:spring-boot-3:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-3
# WebFlux 샘플은 WebTestClient 산출물만 따로 검사한다
./gradlew :samples:spring-boot-4-webflux:apiDocs && python3 scripts/verify-docs-artifacts.py samples/spring-boot-4-webflux

# 4. 실행 앱 검증 (HTTP·SSE·WebSocket·STOMP 17개 항목). jar 를 다시 만들고 다시 띄운다: 전에 띄운 앱은 예전 코드와 문서로 돈다
./gradlew :samples:spring-boot-4:bootJar
java -jar samples/spring-boot-4/build/libs/spring-boot-4-0.1.jar --server.port=18084 &
python3 scripts/verify-live-app.py 18084
# WebFlux 샘플은 verify-live-app.py 대상이 아니다. 띄워서 주소만 확인한다 (Swagger UI 는 /webjars/swagger-ui/index.html)
./gradlew :samples:spring-boot-4-webflux:bootJar
java -jar samples/spring-boot-4-webflux/build/libs/spring-boot-4-webflux-0.1.jar --server.port=18085 &

# 5. 사용자 입장 확인: 로컬 Maven 저장소에 설치(Gradle 플러그인 포함)한 뒤 mavenLocal() 을 쓰는 별도 프로젝트로 빌드한다
./gradlew publishToMavenLocal

# 6. Maven 샘플: 5 단계에서 설치한 아티팩트를 쓴다 (테스트, index, OpenAPI, 뷰어, 커버리지, HTML, 기준 스펙 검사, jar 안의 문서)
cd samples/maven-boot-4 && ./mvnw verify && cd ../..

./gradlew clean                     # Gradle 플러그인 모듈까지 지운다 (clean 만 실행할 때)
```

샘플은 composite build 로 저장소 안의 모듈과 플러그인을 바로 쓰므로 1~4 단계에는 `publishToMavenLocal` 이 필요 없습니다. 배포한 아티팩트로 동작하는지는 5·6 단계에서 확인합니다(Maven 샘플은 배포한 아티팩트만 쓸 수 있음). 5 단계는 저장소 밖의 별도 프로젝트에서 `repositories` 와 `pluginManagement` 에 `mavenLocal()` 을 넣고 `id("io.github.kardx-project")` 을 적용한 뒤 그 프로젝트에서 `./gradlew apiDocs` 를 실행합니다. 샘플 jar 이름은 `samples/<sample>/build/libs/<sample>-<version>.jar` 입니다(`-plain.jar` 는 실행할 수 없는 jar).

외부 도구가 필요하거나 필요할 때만 돌리는 검사:

```bash
# AsyncAPI 공식 파서 검증 (@asyncapi/parser 를 설치한 디렉터리를 넘긴다. 파일 여러 개 가능)
# Boot 3 샘플은 samples/spring-boot-3/build/api-docs/asyncapi.yaml
node scripts/validate-asyncapi.mjs <parser-dir> samples/spring-boot-4/build/api-docs/async/index.yaml

# OpenAPI 검증 (Redocly 권장 규칙. @redocly/openapi-core 1.x 를 설치한 디렉터리를 넘긴다. error 가 있으면 종료 코드 1)
# npm install --prefix /tmp/redocly @redocly/openapi-core@1.34.5
node scripts/validate-openapi.mjs /tmp/redocly samples/spring-boot-4/build/api-docs/openapi3.yaml samples/spring-boot-3/build/api-docs/openapi3.yaml

# 패키지 순환 검사 (core·kotest·kotest5(각각 kotest-common 포함)·Gradle 플러그인·tooling·Maven 플러그인. 순환이 있으면 종료 코드 1, --graph 는 계층별 의존 출력).
# 루트 checkPackageCycles 태스크(check 의 일부)가 같은 검사를 돌리므로 순환이 있으면 ./gradlew build 가 실패한다
python3 scripts/check-package-cycles.py
```

Gradle 플러그인의 다른 Gradle 버전 호환 테스트(`GradleCompatibilityFunctionalTest`)는 배포판이 필요해 평소에는 돌지 않습니다. 버전(내려받음) 또는 풀어 둔 설치 디렉터리를 쉼표로 넘깁니다: `./gradlew -p gradle-plugin test --tests '*GradleCompatibility*' -PgradleVersions=7.6.4,9.0.0`. 핵심 시나리오(플러그인 적용, 의존성 추가와 해석, epages 기본값, 문서 위치 파일, configuration cache 재사용)와 스펙 내보내기·업로드(로컬 HTTP 서버로 `apiDocsExport`·`apiDocsUpload`, 출력에 `deprecated` 가 있으면 실패)를 돌립니다. 7.6.4·8.14.6(래퍼)·9.0.0·9.8.2 에서 확인했습니다.

Kotest 확장 테스트(`./gradlew :kotest:test :kotest5:test`)는 고정 스펙을 Kotest 엔진(`TestEngineLauncher`)으로 실행해 문서·스니펫을 확인합니다. 고정 스펙은 `@EnabledIf(LaunchedOnly::class)` 라 Gradle 이 직접 돌리지 않습니다(테스트 결과에 비활성 스펙으로 보임). 두 모듈의 테스트 클래스패스에는 Kotest Spring 확장을 넣지 않아 Spring 없이 도는 경로를 함께 검증합니다.

**Kotest 5.x·6.x 공유 소스.** `kotest`(6.x)와 `kotest5`(5.x)는 같은 패키지·공개 API 를 다른 Kotest 버전으로 컴파일합니다. 버전과 상관없는 코드(이름 규칙 `TestNames`, 스펙 문서 기록 `SpecDocument`, 실행 상태 `ActiveRun`, 문서화 동작 `LeafTestDocumentation`, `EnumDocumentationExtension`, Spring 컨텍스트 찾기 `SpringContexts`)와 대부분의 테스트·고정 스펙은 `kotest-common/src/{main,test}/kotlin` 에 한 벌만 두고, 컨벤션 플러그인 `restdocs.kotest-module` 이 두 모듈의 소스셋·detekt 대상·Dokka 소스 링크에 넣습니다(배포하지 않는 공유 소스이고 Gradle 프로젝트가 아닙니다). 각 모듈에는 Kotest API 에 닿는 부분만 둡니다.

| 모듈 | main | test |
|---|---|---|
| `kotest-common` | 위 공유 코드 | `RestDocsExtensionTest`·`TestNamesTest`, 고정 스펙, `FixtureRun`(결과 모양) |
| `kotest` (6.x) | `RestDocsExtension`(리스너 구현), `KotestTestCases`(이름 `name.name`·부모·`TestType.Test`) | `runFixtures`(6.x 런처), `LaunchedOnly`(6.x `Condition`), `InstancePerRoot` 고정 스펙, `KotestSixOnlyTest` |
| `kotest5` (5.x) | `RestDocsExtension`, `KotestTestCases`(`name.testName`, `TestType.Dynamic` 도 리프) | `runFixtures`(5.x 런처), `LaunchedOnly`(5.x `EnabledCondition`), `withData`·`extensions()` 고정 스펙, `KotestFiveOnlyTest` |

IntelliJ 는 한 디렉터리를 모듈 하나에만 붙이므로 `kotest-common` 의 파일은 한쪽(보통 `kotest`) 모듈 기준으로 분석됩니다. 다른 쪽 모듈 파일에서 공유 코드의 `internal` 선언이 빨갛게 보일 수 있지만 Gradle 빌드는 두 모듈 모두 정상입니다. 공유 코드를 고치면 `./gradlew :kotest:check :kotest5:check` 로 두 버전을 함께 확인합니다.

공개 API 는 Kotlin `explicitApi()` 로 관리하고 `<모듈>/api/*.api` 로 고정됩니다. API 를 바꾸면 `./gradlew apiDump` 로 갱신해 함께 반영합니다(`check` 가 `apiCheck` 로 검사). `tooling` 은 Java 라서 javap 기반의 `apiDump`/`apiCheck` 가 따로 있습니다(`./gradlew -p tooling apiDump`, 파일 `tooling/api/kardx-tooling.api`. 공개는 `io.github.kardx.tooling` 패키지와 `cli.ApiDocsCli` 뿐). Maven 플러그인의 계약은 목표와 파라미터이고 [Maven 과 명령줄](maven.md)에 적혀 있습니다. 변경 내역은 [CHANGELOG.md](../../CHANGELOG.md) 에 있습니다.

공개 API 에는 data class 를 두지 않습니다. `copy`·`componentN` 이 ABI 에 들어가 프로퍼티를 하나만 추가해도 바이너리 호환이 깨지기 때문입니다. 값 객체(`AsyncApiInfo`, `ResponseWrapper`, `DocumentGenerator.Config` 등)는 일반 class 에 `equals`/`hashCode`/`toString` 을 직접 구현하고(`PublicValueClassesTest` 에 계약 테스트 추가), 이름 있는 인자로 부르는 public 생성자를 둡니다. 이런 클래스에 프로퍼티를 추가할 때는 새 프로퍼티를 기본값과 함께 주 생성자 끝에 붙이고, 바꾸기 전의 생성자 시그니처를 그대로 받는 secondary constructor 를 남겨 새 생성자로 위임합니다(기본값이 있는 생성자는 `$default` 합성 메서드까지 시그니처가 바뀌므로 기존 바이너리가 찾을 수 있게). `apiDump` 결과에서 기존 `<init>` 줄이 지워지지 않고 추가만 되었는지 확인합니다.

모듈 공통 빌드 설정은 `build-logic/` 의 `restdocs.*` 컨벤션 플러그인에 있습니다. 라이브러리 모듈(annotations·core·boot)은 `restdocs.kotlin-library` 가 javadoc jar 를 Dokka 로 채우고, 인터페이스 기본 구현을 `jvmDefault = NO_COMPATIBILITY` 로 내 `DefaultImpls` 를 만들지 않습니다. 모듈 build 파일에는 모듈 이름·설명 같은 모듈별 값만 둡니다. 지원하는 Kotlin 하한(`kotlin-baseline`, 메타데이터 버전과 POM 의 kotlin-stdlib)과 detekt 용 Kotlin(`detekt-kotlin`)은 `gradle/libs.versions.toml` 에서 바꿉니다. 샘플은 문서의 최소 설정처럼 라이브러리 의존성을 적지 않고 Gradle 플러그인에 맡깁니다. 플러그인이 넣는 배포 좌표(`io.github.kardx-project:...`)는 `restdocs.kotlin-sample` 이 같은 빌드의 프로젝트로 바꾸므로 샘플은 늘 지금 소스로 빌드됩니다. 두 샘플(spring-boot-3·4)의 공통 소스는 같아야 하며 `checkSampleSync` 가 검사합니다(WebFlux 샘플은 다른 앱이라 대상이 아닙니다). 의도한 차이는 루트 `build.gradle.kts` 의 허용 목록에 추가합니다(목록과 이유는 [아키텍처 문서](architecture.md#samples). JUnit 문서화 테스트를 Kotest 로 옮긴 스펙은 Boot 4 샘플에만 있습니다).

문서(`docs/*.md`)의 절을 바꾸면 목차를 다시 만듭니다.

```bash
python3 scripts/update-docs-toc.py
```

영문 문서(`README.md`, `docs/*.md`)와 한국어 문서(`README.ko.md`, `docs/ko/*.md`)는 짝이 맞아야 합니다. 어느 쪽이든 고친 뒤 `in step` 이 나올 때까지 짝 검사를 돌립니다. 루트 `checkDocsPairs` 태스크(verification 그룹)가 같은 스크립트를 실행하고 `check` 에 연결돼 있어, 짝이 어긋나면 `./gradlew build` 가 실패합니다. 두 루트 검사 태스크는 `python3` 을 부릅니다. 이름이 다른 환경(Windows 의 `py`)에서는 `-Prestdocs.python=py` 를 줍니다.

```bash
python3 scripts/check-docs-pairs.py
```

짝마다 파일 목록, 제목 구조(개수와 수준. 제목 글은 보지 않음), 코드 블록(개수·언어·코드. 주석은 빼고 한국어 쪽의 한글은 무엇과도 맞음), 표 모양(개수·행·열), 상대 링크 대상 묶음을 비교합니다. 찾은 것마다 `경로:줄: 메시지` 를 출력하고 종료 코드 1 로 끝납니다. 의도한 차이는 어느 쪽 문서든 코드 블록이나 표 바로 위에 표시를 둡니다(사이에는 빈 줄만 됨).

```markdown
<!-- docs-pair: ignore-next-block -->
<!-- docs-pair: ignore-next-table -->
```

오류·경고에는 `RestDocsErrorCode` 의 코드가 붙고(`kardx[KDX-011]: ...`) `docs/troubleshooting.md` 의 그 줄로 이어집니다. 코드를 더하거나 바꾸면 두 문제 해결 문서의 코드 표를 다시 만들고 새 줄의 해결 칸을 씁니다. 루트 `checkErrorCodes` 태스크(`check` 에 연결)가 표가 어긋나거나 해결 칸이 비면 실패합니다.

```bash
python3 scripts/update-error-codes.py
```

## 배포

`.github/workflows/release.yml` 이 라이브러리·Maven 플러그인·`kardx-tooling`(`-all` jar 포함)을 Maven Central(Sonatype Central Portal)에, Gradle 플러그인을 Gradle Plugin Portal 에 배포합니다. 저장소 시크릿이 필요합니다.

| 시크릿 | 용도 |
|---|---|
| `SIGNING_KEY` | ASCII armor PGP 개인 키 (`ORG_GRADLE_PROJECT_signingInMemoryKey` 로 넘긴다) |
| `SIGNING_PASSWORD` | 그 키의 암호 |
| `CENTRAL_USERNAME` | Central Portal 사용자 토큰 이름 |
| `CENTRAL_PASSWORD` | Central Portal 사용자 토큰 암호 |
| `GRADLE_PUBLISH_KEY` | Gradle Plugin Portal API 키 |
| `GRADLE_PUBLISH_SECRET` | Gradle Plugin Portal API 시크릿 |

1. `gradle.properties` 의 `version` 을 정하고(예: `0.1`) `main` 에 합칩니다.
2. 그 커밋에 `v<버전>` 태그를 달고 push 합니다. 태그와 버전이 다르면 워크플로가 실패합니다.
3. 워크플로가 `build`, `publishToStaging`(서명해서 `build/staging-deploy` 로), `scripts/central-upload.sh`, `gradle-plugin` 의 `publishPlugins` 를 돌리고 자동 생성 노트로 GitHub 릴리스를 만듭니다.
4. Central 배포는 `USER_MANAGED` 입니다: `VALIDATED` 가 되면 Central Portal(Deployments)에서 Publish 를 누릅니다. 그 전에는 Plugin Portal 의 Gradle 플러그인이 `kardx-tooling` 을 받지 못합니다.

```bash
git tag v0.1
git push origin v0.1
```

`signingInMemoryKey` 가 없으면 `build`·`publishToMavenLocal` 은 서명하지 않습니다. 로컬에서 묶음을 확인하려면 키를 주고 `./gradlew publishToStaging` 뒤 `scripts/central-upload.sh --dry-run` 을 돌립니다 (모든 `.jar`/`.pom`/`.module` 에 `.asc`·`.md5`·`.sha1` 이 있는지 보고, 올리지는 않습니다). 예전 실행의 파일이 남아 있으면 먼저 `build/staging-deploy` 를 지웁니다. Gradle 플러그인 본체와 마커도 Central 에 올라가므로 `pluginManagement { repositories { mavenCentral() } }` 로도 쓸 수 있습니다.
