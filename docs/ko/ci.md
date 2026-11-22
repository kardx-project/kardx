[English](../ci.md) | **한국어**

# CI 적용

[문서 목록](README.md)

<!-- toc -->
**목차**

- [무엇을 돌리나](#무엇을-돌리나)
- [PR 검사](#pr-검사)
  - [빌드 설정](#빌드-설정)
  - [워크플로](#워크플로)
- [PR 댓글](#pr-댓글)
- [의도한 API 변경](#의도한-api-변경)
- [main 에서 문서 배포](#main-에서-문서-배포)
  - [GitHub Pages](#github-pages)
  - [스펙을 레지스트리로 보내기](#스펙을-레지스트리로-보내기)
- [속도](#속도)
- [여러 프로젝트](#여러-프로젝트)
- [GitLab CI](#gitlab-ci)
<!-- /toc -->

문서 파이프라인을 CI 에 붙이는 설정을 복사해 쓸 수 있게 모았습니다. 깨는 API 변경과 문서화하지 않은 엔드포인트가 있으면 PR 을 실패시키고, 보고서를 PR 댓글로 남기고, `main` 에서 문서를 배포합니다. GitHub Actions 를 먼저 다루고, 끝에 GitLab CI 의 짧은 예를 둡니다. 여기서 쓰는 태스크와 설정은 [빌드와 산출물](build.md)에 설명이 있습니다.

`apiDocsCheck`, `apiDocsBaseline`, `apiDocsCoverage` 는 OpenAPI 스펙을 다루므로 epages 플러그인(`com.epages.restdocs-api-spec`)을 적용했을 때만 생깁니다.

## 무엇을 돌리나

| 목적 | 태스크 | 설정 | 보고서 |
|---|---|---|---|
| 테스트가 실패하면 빌드 실패 | `build` (`check` → `test`) | 없음 | Gradle 테스트 보고서 |
| 깨는 API 변경이 있으면 빌드 실패 | `apiDocsCheck` | `openApiCheck { failOn }` | `build/reports/kardx/openapi-changes.txt` |
| 문서화하지 않은 엔드포인트가 있으면 빌드 실패 | `apiDocs` (`apiDocsCoverage` 포함) | `coverage { failOnUndocumented = true }` | `build/api-docs/coverage/coverage.md`, `coverage.json` |
| 문서 사이트 | `apiDocs` | 없음 | `build/api-docs/` |
| 스펙을 레지스트리로 보내기 | `apiDocsUpload` | `upload { url }` | 없음 |

`build` 는 `bootJar.enabled`(기본값)인 Spring Boot 앱일 때만 `apiDocs` 를 끌고 오고, `apiDocsCheck` 는 `openApiCheck { runOnCheck = true }` 일 때만 포함합니다([상황별 명령과 순서](build.md#상황별-명령과-순서)). 두 태스크를 명령줄에 함께 적으면 어느 경우에나 돌므로 아래 예는 그렇게 합니다.

## PR 검사

### 빌드 설정

```kotlin
// build.gradle.kts
kardx {
    openApiCheck {
        failOn = "breaking"         // 하위 호환을 깨는 변경에서만 실패 (기본값 "any": 차이가 있으면 실패)
    }
    coverage {
        failOnUndocumented = true   // 문서화 테스트가 없는 엔드포인트가 있으면 실패
    }
}
```

- `failOn = "any"`(기본값)는 엔드포인트 추가나 설명 변경에서도 실패하므로 API 를 바꿀 때마다 기준을 새로 만들어야 합니다. `"breaking"` 은 [OpenAPI 변경 비교](checks.md#openapi-변경-비교)에 적힌 변경에서만 실패하고 나머지는 경고로 출력합니다.
- `apiDocsCheck` 에는 커밋한 기준 파일(`docs/openapi/openapi3.yaml`)이 필요합니다. 없으면 `No OpenAPI baseline` 으로 실패하므로, 검사를 켜기 전에 [의도한 API 변경](#의도한-api-변경)처럼 기준을 만들어 둡니다.
- 로컬 빌드는 두고 CI 에서만 문서화 누락으로 실패시키려면 [문서화 커버리지](checks.md#문서화-커버리지)에 있는 것처럼 Gradle 속성으로 값을 받습니다.

### 워크플로

```yaml
# .github/workflows/api-docs.yml
name: API docs

on:
  pull_request:

permissions:
  contents: read

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v6
      # --continue: 커버리지 검사가 실패해도 apiDocsCheck 가 돌아 두 보고서가 모두 남는다
      - name: Build, compare with the baseline, check coverage
        run: ./gradlew build apiDocs apiDocsCheck --continue
      - name: Upload API docs
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: api-docs
          path: |
            build/api-docs/
            build/reports/kardx/
          if-no-files-found: ignore
```

- `java-version` 에는 프로젝트를 빌드하는 JDK 를 적습니다.
- Gradle 을 한 번 실행하면 테스트도 한 번만 돕니다. `build`, `apiDocs`, `apiDocsCheck` 를 job 이나 실행으로 나누면 테스트가 매번 다시 돕니다.
- 테스트가 실패하거나, `apiDocsCheck` 가 실패하거나, `failOnUndocumented = true` 에서 `apiDocsCoverage` 가 문서화하지 않은 엔드포인트를 찾으면 job 이 실패합니다. 아티팩트에는 생성 문서(`coverage/` 포함)와 변경 보고서가 들어갑니다.

## PR 댓글

변경 보고서와 커버리지 보고서를 PR 에 남깁니다. push 할 때마다 댓글을 새로 달지 않고 하나를 고쳐 씁니다. 위 `check` job 끝에 다음 단계를 붙이고, job 에 PR 쓰기 권한을 줍니다.

```yaml
jobs:
  check:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      # ... 위 check job 의 단계들 ...
      - name: Write the comment
        if: always() && github.event_name == 'pull_request'
        run: |
          {
            echo '## API docs'
            echo
            echo '### OpenAPI changes'
            echo
            echo '~~~text'
            head -n 200 build/reports/kardx/openapi-changes.txt 2>/dev/null || echo 'apiDocsCheck did not run'
            echo '~~~'
            echo
            cat build/api-docs/coverage/coverage.md 2>/dev/null || echo 'apiDocsCoverage did not run'
          } > api-docs-comment.md
      - name: Post the comment
        if: always() && github.event_name == 'pull_request'
        uses: marocchino/sticky-pull-request-comment@v2
        with:
          header: api-docs
          path: api-docs-comment.md
```

- `apiDocsCheck` 는 돌 때마다 `openapi-changes.txt` 를 씁니다. 기준이 있으면 `The OpenAPI spec matches the baseline (…)` 또는 `The OpenAPI spec differs from the baseline (…)` 로 시작하고 `Breaking changes: N`, `All structural differences: N`(바뀐 것이 없으면 둘 다 `0`)과 전체 목록이 이어집니다. 기준이 없으면 기준 파일이 없다는 안내가 들어갑니다. 댓글에는 앞 200줄만 넣고, 전체 보고서는 아티팩트에 있습니다.
- `apiDocsCoverage` 는 돌 때마다 문서화하지 않은 엔드포인트와 핸들러를 담은 `coverage.md` 를 쓰고, `apiDocs` 가 이를 `build/api-docs/coverage/` 로 복사합니다.
- `if: always()` 라서 빌드가 실패했을 때도 댓글을 남깁니다. 댓글이 필요한 때가 바로 그때입니다. 테스트가 실패하면 두 태스크 모두 돌지 않고, 댓글에 그렇게 나옵니다.
- `header` 값이 같으면 PR 마다 댓글이 하나로 유지되고 다음 실행이 그 댓글을 바꿉니다.
- 포크에서 온 PR 은 `GITHUB_TOKEN` 이 읽기 전용이라 댓글 단계가 실패합니다. 아티팩트는 그대로 올라갑니다.
- 외부 액션 없이 `gh pr comment ${{ github.event.pull_request.number }} --body-file api-docs-comment.md` (`env` 에 `GH_TOKEN: ${{ github.token }}`)로도 올릴 수 있지만, 이 경우 실행마다 댓글이 새로 달립니다.

## 의도한 API 변경

PR 에서 API 를 일부러 바꾸면 기준을 갱신하기 전까지 `apiDocsCheck` 가 실패합니다. 로컬에서 갱신해 변경과 함께 커밋합니다.

```bash
./gradlew apiDocsBaseline   # 현재 스펙을 docs/openapi/openapi3.yaml 에 저장
git add docs/openapi/openapi3.yaml
git commit -m "Update the OpenAPI baseline"
```

- 리뷰어는 PR 의 `docs/openapi/openapi3.yaml` diff 로 API 변경을 봅니다. API 변경에 특정 리뷰어를 지정하려면 이 파일을 `CODEOWNERS` 에 넣습니다.
- `./gradlew apiDocsBaseline apiDocsCheck` 는 한 번 실행으로 기준을 갱신한 뒤 그 기준으로 검사합니다.
- 기준 파일 위치는 `openApiCheck { baseline }` 입니다. `openapi3 { format = "json" }` 이면 기준 파일도 `.json` 으로 둡니다.
- 스펙에 실행마다 바뀌는 값(예시의 시각, 무작위 ID)이 있으면 검사가 매번 실패합니다. 예시 값은 고정합니다.

## main 에서 문서 배포

### GitHub Pages

`build/api-docs/` 는 정적 사이트입니다. `index.html`(REST Docs. Asciidoctor 플러그인이 있을 때만. 없으면 뷰어를 켜고 `openapi.html` 로 잇습니다), `openapi3.yaml`, 메시지를 문서화했다면 `asyncapi.html`, [OpenAPI 뷰어](outputs.md#openapi-뷰어-redoc--scalar)를 켰다면 `openapi.html` 이 들어 있습니다. Swagger UI 는 springdoc 이 앱에서 띄우므로 여기에 없습니다. 정적 호스팅에서 볼 스펙 페이지가 필요하면 뷰어를 켭니다.

```yaml
# .github/workflows/api-docs-pages.yml
name: Publish API docs

on:
  push:
    branches: [main]

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew apiDocs
      - uses: actions/upload-pages-artifact@v3
        with:
          path: build/api-docs
  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

- **Settings → Pages → Source** 를 한 번 **GitHub Actions** 로 바꿉니다.
- 문서에는 테스트가 실제로 보낸 요청과 응답이 들어갑니다. [민감 헤더 마스킹](configuration.md#민감-정보-마스킹)을 켜지 않았다면 테스트에서 쓴 토큰과 쿠키도 함께 배포됩니다. 특히 공개 사이트라면 배포 전에 확인합니다([운영 노출](build.md#운영-노출)).
- Redoc·Scalar 뷰어는 페이지를 열 때 unpkg CDN 에서 스크립트를 받습니다.
- 브랜치 폴더에서 배포하려면(**Deploy from a branch**, `/docs`) `outputDirectory` 나 `aggregate.outputDirectory` 를 저장소의 `docs/` 로 둘 수 있습니다. 플러그인이 쓰기 전에 그 디렉터리를 비우므로 `build/` 밖에서는 빈 디렉터리나 표시 파일 `.kardx-output` 이 있는 디렉터리만 받습니다 ([플러그인이 비우는 디렉터리](build.md#플러그인이-비우는-디렉터리)). 직접 쓴 파일이 있는 `docs/` 를 가리키지 마세요.

### 스펙을 레지스트리로 보내기

플러그인이 스펙을 HTTP 로 직접 올릴 수 있습니다([apiDocsUpload](outputs.md#apidocsupload)). 토큰은 환경 변수로 넘겨 빌드 스크립트에 남지 않게 합니다.

```kotlin
// build.gradle.kts
kardx {
    upload {
        url = "https://registry.example.com/apis/{name}/{version}/{type}"
        headers.put("Authorization", providers.environmentVariable("API_REGISTRY_TOKEN").map { "Bearer $it" })
    }
}
```

```yaml
      # main 에서 도는 job 의 한 단계
      - name: Upload specs
        env:
          API_REGISTRY_TOKEN: ${{ secrets.API_REGISTRY_TOKEN }}
        run: ./gradlew apiDocsUpload
```

- `apiDocsUpload` 는 실행할 때마다 보내고(UP-TO-DATE 가 되지 않음), 로그에는 헤더 값이 아니라 이름만 남깁니다.
- 스펙 파일을 빌드 결과로 남기려면 `export { directory }` 를 정하고 `./gradlew apiDocsExport` 를 돌린 뒤 그 디렉터리를 아티팩트로 올립니다. Maven 배포물에 붙이려면 `export { publishSpecs = true }` 로 두고 `./gradlew publish` 를 돌립니다([스펙 내보내기](outputs.md#스펙-내보내기)).
- 어느 것도 `build` 에 들어 있지 않으니, 스펙을 보낼 job 에서 직접 호출합니다.

## 속도

- `gradle/actions/setup-gradle` 이 Gradle 의존성과 wrapper 를 실행 사이에 캐시합니다.
- 위 예처럼 모든 태스크를 Gradle 한 번 실행으로 돌립니다. 각 태스크가 필요한 태스크를 먼저 돌리므로 `test` 나 `openapi3` 를 따로 적지 않아도 됩니다.
- `apiDocsCheck` 는 UP-TO-DATE 로 건너뛰지 않고 매번 비교합니다. 같은 실행 안에서 테스트를 다시 돌리지는 않습니다.
- 이 플러그인의 태스크는 configuration cache 와 호환되지만 `org.asciidoctor.jvm.convert` 4.0.x 와 epages 플러그인은 아직 아니어서, 파이프라인 전체에 `--configuration-cache` 를 켜면 그 플러그인들의 문제가 보고됩니다([Gradle 플러그인](build.md#gradle-플러그인)). 이 job 에서는 끄거나 `--configuration-cache-problems=warn` 을 씁니다.
- Gradle build cache 를 켜면 `apiDocsCoverage` 가 캐시에서 복원될 수 있습니다. 이때 콘솔 요약은 나오지 않지만 `coverage.md` 는 `build/api-docs/coverage/` 에 그대로 있습니다.

## 여러 프로젝트

- 루트에서 `./gradlew build apiDocs apiDocsCheck` 를 돌리면 각 태스크가 그 태스크를 가진 모든 프로젝트에서 돕니다. 프로젝트마다 자기 기준 파일(`<project>/docs/openapi/openapi3.yaml`)과 비교하고 보고서를 자기 `build/` 아래에 쓰므로, 아티팩트와 댓글 단계에서는 `**/build/api-docs/coverage/coverage.md` 같은 패턴으로 모읍니다.
- 사이트 하나로 배포하려면 모으는 프로젝트에서 `restdocsAggregation(...)` 으로 프로젝트를 선언하고 `./gradlew apiDocsAggregate` 를 돌린 뒤 `build/api-docs` 대신 `build/api-docs-aggregate/` 를 올립니다([여러 프로젝트 모으기](outputs.md#여러-프로젝트-모으기)). 목록 페이지, 합친 `openapi3.yaml`, 프로젝트별 문서 사본이 들어 있습니다.

## GitLab CI

같은 파이프라인을 `.gitlab-ci.yml` 로 옮긴 예입니다. 검사는 merge request 와 기본 브랜치에서 돌고, `pages` 가 기본 브랜치의 `build/api-docs` 를 배포합니다.

```yaml
# .gitlab-ci.yml
variables:
  GRADLE_USER_HOME: "$CI_PROJECT_DIR/.gradle"

default:
  image: eclipse-temurin:17-jdk
  cache:
    key: gradle
    paths:
      - .gradle/caches
      - .gradle/wrapper

api-docs:
  script:
    - ./gradlew build apiDocs apiDocsCheck --continue
  artifacts:
    when: always
    paths:
      - build/api-docs/
      - build/reports/kardx/
  rules:
    - if: $CI_PIPELINE_SOURCE == "merge_request_event"
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH

pages:
  script:
    - ./gradlew apiDocs
    - cp -r build/api-docs public
  artifacts:
    paths:
      - public
  rules:
    - if: $CI_COMMIT_BRANCH == $CI_DEFAULT_BRANCH
```

- 보고서는 job 아티팩트에 있습니다. merge request 에 남기려면 [PR 댓글](#pr-댓글)처럼 만든 `api-docs-comment.md` 를 프로젝트 액세스 토큰으로 GitLab notes API 에 보냅니다.
- `apiDocsUpload` 를 쓰려면 `API_REGISTRY_TOKEN` 을 masked CI/CD 변수로 정의합니다. GitLab 이 job 에 환경 변수로 넘깁니다.
