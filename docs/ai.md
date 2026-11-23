**English** | [한국어](ko/ai.md)

# AI assistants

[Document list](README.md)

<!-- toc -->
**Contents**

- [What there is](#what-there-is)
- [Claude Code](#claude-code)
- [Other assistants](#other-assistants)
- [Tips](#tips)
<!-- /toc -->

kardx ships material that helps AI coding assistants write correct documentation tests: a condensed guide in the
[llms.txt](https://llmstxt.org) format, the whole documentation as one text file, and a ready-made Claude Code skill.
The examples in the skill are compiled and run against the published artifacts.

## What there is

| File | Address | Use |
|---|---|---|
| `llms.txt` | `https://kardx-project.github.io/kardx/llms.txt` | Short overview, coordinates and links to every page as raw Markdown. Give this address to an assistant that can fetch URLs |
| `llms-full.txt` | `https://kardx-project.github.io/kardx/llms-full.txt` | All English pages in one file (about 170k tokens). For tools that index a whole document |
| Claude Code skill | [`skills/kardx/SKILL.md`](https://github.com/kardx-project/kardx/blob/main/skills/kardx/SKILL.md) | Setup, test skeletons, the `andDocument { }` DSL, settings and error codes on one page |
| `AGENTS.md` | [`AGENTS.md`](https://github.com/kardx-project/kardx/blob/main/AGENTS.md) | For agents working on the kardx repository itself, not for using the library |

## Claude Code

Copy the skill into your project. Claude Code loads it when a task involves API documentation tests.

```bash
mkdir -p .claude/skills/kardx
curl -fsSL https://raw.githubusercontent.com/kardx-project/kardx/main/skills/kardx/SKILL.md \
  -o .claude/skills/kardx/SKILL.md
```

To make it available in every project, put it in `~/.claude/skills/kardx/` instead. Commit `.claude/skills/` so the whole team gets it.

## Other assistants

Most assistants read a rules file from the repository. Download the same skill to the file your tool reads.

| Tool | File |
|---|---|
| GitHub Copilot | `.github/copilot-instructions.md` (append) |
| Cursor | `.cursor/rules/kardx.mdc` |
| Codex and other `AGENTS.md` readers | `AGENTS.md` (append) |

```bash
curl -fsSL https://raw.githubusercontent.com/kardx-project/kardx/main/skills/kardx/SKILL.md \
  -o .cursor/rules/kardx.mdc
```

Assistants that can fetch web pages can also be pointed at `llms.txt` in the prompt, for example: "Use kardx for the
API docs. Read https://kardx-project.github.io/kardx/llms.txt first."

## Tips

- Ask for tests that document real requests. kardx verifies documented fields against the actual response, so a
  passing test means the document matches the API.
- When a test fails with a `KDX-NNN` code, give the assistant the whole message. It contains the fix and a link to
  [Troubleshooting](troubleshooting.md).
- Missing or extra fields are reported with the DSL lines to paste in ([Verification failure messages](http-api.md#verification-failure-messages)).
- Run `./gradlew apiDocs` after the change and check `build/api-docs/` ([Build and outputs](build.md)).
