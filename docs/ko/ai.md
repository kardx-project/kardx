[English](../ai.md) | **한국어**

# AI 도우미

[문서 목록](README.md)

<!-- toc -->
**목차**

- [제공하는 것](#제공하는-것)
- [Claude Code](#claude-code)
- [다른 도우미](#다른-도우미)
- [팁](#팁)
<!-- /toc -->

kardx 는 AI 코딩 도우미가 문서화 테스트를 올바르게 쓰도록 돕는 자료를 함께 제공합니다. [llms.txt](https://llmstxt.org)
형식의 요약 안내, 전체 문서를 한 파일로 묶은 텍스트, 바로 쓸 수 있는 Claude Code 스킬입니다.
스킬의 예시는 배포된 아티팩트로 컴파일하고 실행해 확인했습니다.

## 제공하는 것

| 파일 | 주소 | 용도 |
|---|---|---|
| `llms.txt` | `https://kardx-project.github.io/kardx/llms.txt` | 짧은 개요, 좌표, 모든 문서의 원본 Markdown 링크. URL 을 읽을 수 있는 도우미에게 이 주소를 준다 |
| `llms-full.txt` | `https://kardx-project.github.io/kardx/llms-full.txt` | 영어 문서 전체를 한 파일로(약 17만 토큰). 문서 전체를 색인하는 도구용 |
| Claude Code 스킬 | [`skills/kardx/SKILL.md`](https://github.com/kardx-project/kardx/blob/main/skills/kardx/SKILL.md) | 설정, 테스트 뼈대, `andDocument { }` DSL, 설정값, 오류 코드를 한 페이지에 |
| `AGENTS.md` | [`AGENTS.md`](https://github.com/kardx-project/kardx/blob/main/AGENTS.md) | kardx 저장소 자체를 고치는 에이전트용. 라이브러리 사용과는 관계없음 |

## Claude Code

스킬을 프로젝트에 복사합니다. API 문서화 테스트와 관련된 작업이면 Claude Code 가 스킬을 불러옵니다.

```bash
mkdir -p .claude/skills/kardx
curl -fsSL https://raw.githubusercontent.com/kardx-project/kardx/main/skills/kardx/SKILL.md \
  -o .claude/skills/kardx/SKILL.md
```

모든 프로젝트에서 쓰려면 `~/.claude/skills/kardx/` 에 둡니다. `.claude/skills/` 를 커밋하면 팀 전체가 함께 씁니다.

## 다른 도우미

대부분의 도우미는 저장소의 규칙 파일을 읽습니다. 같은 스킬을 쓰는 도구가 읽는 파일로 내려받습니다.

| 도구 | 파일 |
|---|---|
| GitHub Copilot | `.github/copilot-instructions.md` (뒤에 덧붙임) |
| Cursor | `.cursor/rules/kardx.mdc` |
| Codex 등 `AGENTS.md` 를 읽는 도구 | `AGENTS.md` (뒤에 덧붙임) |

```bash
curl -fsSL https://raw.githubusercontent.com/kardx-project/kardx/main/skills/kardx/SKILL.md \
  -o .cursor/rules/kardx.mdc
```

웹 페이지를 읽을 수 있는 도우미라면 프롬프트에서 `llms.txt` 를 알려 줘도 됩니다. 예: "API 문서는 kardx 로 만들어 줘.
먼저 https://kardx-project.github.io/kardx/llms.txt 를 읽어."

## 팁

- 실제 요청을 문서화하는 테스트를 요청합니다. kardx 는 문서에 적은 필드를 실제 응답과 대조하므로 테스트가 통과하면
  문서가 API 와 맞습니다.
- 테스트가 `KDX-NNN` 코드로 실패하면 메시지 전체를 도우미에게 줍니다. 해결 방법과
  [트러블슈팅](troubleshooting.md) 링크가 들어 있습니다.
- 빠지거나 남는 필드는 붙여 넣을 DSL 줄과 함께 알려 줍니다([검증 실패 메시지](http-api.md#검증-실패-메시지)).
- 바꾼 뒤 `./gradlew apiDocs` 를 실행하고 `build/api-docs/` 를 확인합니다([빌드와 산출물](build.md)).
