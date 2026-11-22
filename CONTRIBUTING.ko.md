[English](CONTRIBUTING.md) | **한국어**

# 기여 안내

kardx 에 관심을 가져 주셔서 감사합니다. 버그 제보, 기능 제안, 문서 개선, 코드 기여 모두 환영합니다.

## 이슈

- 버그와 기능 제안은 GitHub 이슈 양식으로 남겨 주세요. 버그는 재현 코드와 Spring Boot·REST Docs·Kotlin·JDK·Gradle 버전을 함께 적어 주시면 빠르게 확인할 수 있습니다.
- 보안 취약점은 공개 이슈로 올리지 말고 [SECURITY.md](SECURITY.md) 의 방법으로 제보해 주세요.
- 큰 변경은 먼저 이슈에서 방향을 맞춘 뒤 작업하면 서로의 시간을 아낄 수 있습니다.

## 개발 환경

모듈 구성, 로컬 설치, 검증 스크립트 사용법은 [docs/ko/development.md](docs/ko/development.md) 에 있습니다.

- JDK 17 이상 (gradle-plugin 은 toolchain 17 을 사용하므로 JDK 17 이 설치되어 있어야 합니다)
- Gradle 은 저장소의 wrapper(`./gradlew`)를 사용합니다

## 빌드와 검증

검증 순서와 명령의 기준 문서는 [docs/ko/development.md](docs/ko/development.md) 입니다. 최소한 다음을 확인해 주세요.

```bash
# 빌드, 테스트, ktlint, detekt, apiCheck, checkSampleSync, 문서 짝 검사 checkDocsPairs (샘플 문서 생성, Gradle 플러그인 모듈 포함)
./gradlew build

# 공개 API 를 바꿨다면 api 파일을 갱신해 함께 올린다 (apiCheck 가 확인)
./gradlew apiDump

# 생성된 문서 산출물 검증 (실패가 있으면 종료 코드 1). CI 는 세 샘플 모두 검사한다
python3 scripts/verify-docs-artifacts.py samples/spring-boot-3
python3 scripts/verify-docs-artifacts.py samples/spring-boot-4
python3 scripts/verify-docs-artifacts.py samples/spring-boot-4-webflux

# AsyncAPI 공식 파서 검증 (@asyncapi/parser 를 설치한 디렉터리를 넘긴다)
node scripts/validate-asyncapi.mjs <parser-dir> \
  samples/spring-boot-3/build/api-docs/asyncapi.yaml \
  samples/spring-boot-4/build/api-docs/async/index.yaml
```

PR 을 올리면 CI 가 JDK 17·21 에서 같은 검증을 실행합니다. Boot 3 와 Boot 4 샘플의 공통 소스는 같은 내용이어야 하며 `checkSampleSync` 가 확인합니다.

## 코드 스타일

- `.editorconfig` 와 ktlint·detekt 규칙을 따릅니다. `./gradlew build` 가 통과하면 됩니다.
- core 테스트에서 `RestDocsConfiguration` 은 Kotest 프로젝트 설정(`io.kotest.provided.ProjectConfig`)이 매 테스트 뒤 되돌립니다. 테스트 하나에서만 쓸 설정은 `withSettings` 를 씁니다.
- 공개 API 는 Kotlin `explicitApi()` 로 관리합니다. 공개 API 를 추가·변경하면 KDoc 과 문서(`docs/`)를 함께 갱신해 주세요.
- 사용자에게 보이는 변경은 [CHANGELOG.md](CHANGELOG.md) 에 적어 주세요.
- 문서는 영어(`README.md`, `docs/`)와 한국어(`README.ko.md`, `docs/ko/`) 두 벌입니다. 한쪽을 고치면 다른 쪽도 같은 PR 에서 함께 고쳐 주세요.
- `docs/*.md`·`docs/ko/*.md` 의 절을 바꾸면 `python3 scripts/update-docs-toc.py` 로 두 언어의 목차를 다시 만듭니다.
- 두 언어 문서의 짝(파일 목록, 제목 구조, 코드 블록, 표, 링크 대상)은 `python3 scripts/check-docs-pairs.py` 로 확인합니다. 루트 `checkDocsPairs` 태스크가 같은 검사를 `check` 에서 돌리므로 `./gradlew build` 도 짝이 어긋나면 실패합니다.

## 커밋 메시지

```
<type>: <description>

<본문 (선택)>
```

| type | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 개선 |
| `docs` | 문서 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드 설정, 의존성 등 기타 |
| `perf` | 성능 개선 |
| `ci` | CI 설정 |

예: `fix: nullable 컬렉션 필드의 optional 판정 수정`

## Pull Request

- PR 템플릿의 체크리스트(`./gradlew build` 통과, 문서·CHANGELOG 갱신)를 확인해 주세요.
- 하나의 PR 에는 하나의 관심사만 담아 주세요.
- 기여한 코드는 저장소와 같은 [Apache License 2.0](LICENSE) 으로 배포됩니다.
