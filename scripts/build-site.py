#!/usr/bin/env python3
"""Prepare docs/ for the mkdocs site.

docs/*.md (English) and docs/ko/*.md (Korean) are written for GitHub, so this
copies them into build/site-src/{en,ko} and adjusts what GitHub renders but
mkdocs does not:

- README.md becomes index.md
- the "English | 한국어" line is dropped (the site has a language switcher)
- links to other pages stay relative; links to another language, or to files
  outside docs/ (samples, README, ...), become absolute URLs
- a blank line is inserted before a list that directly follows a paragraph

Usage: python3 scripts/build-site.py   (then: mkdocs build -f site/mkdocs.yml)
"""
import os
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
OUT = ROOT / "build" / "site-src"
SITE_URL = "https://kardx-project.github.io/kardx/"
REPO_BLOB = "https://github.com/kardx-project/kardx/blob/main/"
REPO_TREE = "https://github.com/kardx-project/kardx/tree/main/"

LANGS = {"en": DOCS, "ko": DOCS / "ko"}
LANG_SWITCH = re.compile(r"^\s*(\*\*English\*\*\s*\|\s*\[한국어\]|\[English\]\([^)]*\)\s*\|\s*\*\*한국어\*\*)")
LINK = re.compile(r"(!?\[[^\]]*\]\()([^)\s]+)(\))")
LIST_ITEM = re.compile(r"^\s*([-*+]|\d+\.)\s")
FENCE = re.compile(r"^\s*(```|~~~)")


def page_name(name: str) -> str:
    return "index.md" if name == "README.md" else name


def lang_of(path: Path):
    """Return (lang, page) if path is a doc page, else None."""
    try:
        rel = path.relative_to(DOCS)
    except ValueError:
        return None
    parts = rel.parts
    if len(parts) == 1 and parts[0].endswith(".md"):
        return "en", page_name(parts[0])
    if len(parts) == 2 and parts[0] == "ko" and parts[1].endswith(".md"):
        return "ko", page_name(parts[1])
    return None


def page_url(lang: str, page: str) -> str:
    slug = "" if page == "index.md" else page[:-3] + "/"
    return SITE_URL + ("ko/" if lang == "ko" else "") + slug


def rewrite_link(target: str, src_dir: Path, lang: str) -> str:
    if re.match(r"^[a-z]+:", target) or target.startswith("#"):
        return target
    path_part, _, anchor = target.partition("#")
    anchor = "#" + anchor if anchor else ""
    if path_part.startswith("/"):
        return target  # e.g. /docs/index.html on the user's own app
    resolved = Path(os.path.normpath(src_dir / path_part))
    doc = lang_of(resolved)
    if doc:
        tlang, tpage = doc
        if tlang == lang:
            return tpage + anchor
        return page_url(tlang, tpage) + anchor
    if resolved.exists():
        rel = resolved.relative_to(ROOT).as_posix()
        return (REPO_TREE if resolved.is_dir() else REPO_BLOB) + rel + anchor
    return target


def convert(text: str, src_dir: Path, lang: str) -> str:
    lines = text.split("\n")
    if lines and LANG_SWITCH.match(lines[0]):
        lines = lines[1:]
        while lines and not lines[0].strip():
            lines = lines[1:]
    out = []
    in_fence = False
    for line in lines:
        if FENCE.match(line):
            in_fence = not in_fence
            out.append(line)
            continue
        if not in_fence:
            line = LINK.sub(lambda m: m.group(1) + rewrite_link(m.group(2), src_dir, lang) + m.group(3), line)
            prev = out[-1] if out else ""
            if (LIST_ITEM.match(line) and prev.strip() and not LIST_ITEM.match(prev)
                    and not prev.startswith((" ", "\t", "|", ">", "#"))):
                out.append("")
        out.append(line)
    return "\n".join(out)


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    for lang, src_dir in LANGS.items():
        dest = OUT / lang
        dest.mkdir(parents=True)
        for src in sorted(src_dir.glob("*.md")):
            text = convert(src.read_text(encoding="utf-8"), src_dir, lang)
            (dest / page_name(src.name)).write_text(text, encoding="utf-8")
    assets = ROOT / "site" / "assets"
    if assets.exists():
        shutil.copytree(assets, OUT / "en" / "assets")
    write_llms_txt(OUT / "en")
    print(f"wrote {OUT.relative_to(ROOT)}")


# --- llms.txt (https://llmstxt.org) -------------------------------------------------------------

RAW = "https://raw.githubusercontent.com/kardx-project/kardx/main/"
LLMS_HEADER = """# kardx

> kardx (Kotlin Auto Rest Docs eXtension) is a Kotlin DSL for Spring REST Docs. Documentation tests
> (MockMvc, WebTestClient, RestTestClient; JUnit or Kotest) produce REST Docs snippets and HTML, an OpenAPI 3 spec
> (Swagger UI, Redoc/Scalar viewer), AsyncAPI for WebSocket/STOMP/SSE, Postman and IntelliJ HTTP Client files and
> WireMock stubs. Fields are extracted from DTO types and verified against real responses.

- Coordinates: group `io.github.kardx-project`, artifacts `kardx`, `kardx-annotations`, `kardx-kotest`,
  `kardx-kotest5`, `kardx-boot`, `kardx-bom`, `kardx-tooling`, `kardx-maven-plugin`; Gradle plugin id
  `io.github.kardx-project` (also on Maven Central: add `mavenCentral()` to `pluginManagement`). Packages `io.github.kardx.*`.
- Supports Spring Boot 3.5 (REST Docs 3, epages 0.19.x) and 4.x (REST Docs 4, epages 0.20.x), JDK 17+, Kotlin 2.2+.
- The Gradle plugin adds the library dependencies itself; don't declare `kardx`, `spring-restdocs-*` or
  `restdocs-api-spec-*` by hand. Run `./gradlew apiDocs`; outputs go to `build/api-docs/`.
- Documentation tests are written in Kotlin; app code and DTOs may be Java.
"""
LLMS_EXTRA = """
## For AI assistants

- [Claude Code skill]({raw}skills/kardx/SKILL.md): condensed, verified guide for writing kardx documentation tests
- [Full documentation in one file]({site}llms-full.txt): every English page above, concatenated (about 170k tokens)
- [Contributor guide for agents]({raw}AGENTS.md): working on the kardx repository itself

## Optional

- [Korean documentation]({site}ko/): the same pages in Korean (`docs/ko/*.md`)
- [Changelog]({raw}CHANGELOG.md)
- [Samples]({tree}samples): Boot 3, Boot 4, WebFlux and Maven sample projects
"""


def nav_pages():
    """Pages in the order of the site navigation (mkdocs.yml nav)."""
    nav = (ROOT / "site" / "mkdocs.yml").read_text(encoding="utf-8").split("\nnav:", 1)[1]
    return re.findall(r"-\s+([\w-]+\.md)\s*$", nav, re.M)


def page_summaries():
    """'Contents' column of the document table in docs/README.md, keyed by file name."""
    summaries = {}
    for line in (DOCS / "README.md").read_text(encoding="utf-8").splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) >= 3:
            m = re.search(r"\]\(([\w-]+\.md)[^)]*\)", cells[1])
            if m:
                summaries.setdefault(m.group(1), cells[2])
    return summaries


def title_of(path: Path) -> str:
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("# "):
            return line[2:].strip()
    return path.stem


def write_llms_txt(dest: Path):
    summaries = page_summaries()
    pages = [p for p in nav_pages() if p != "index.md"]
    lines = [LLMS_HEADER, "## Docs", ""]
    for page in pages:
        src = DOCS / page
        summary = summaries.get(page, "")
        lines.append(f"- [{title_of(src)}]({RAW}docs/{page})" + (f": {summary}" if summary else ""))
    lines.append(LLMS_EXTRA.format(raw=RAW, site=SITE_URL, tree=REPO_TREE))
    (dest / "llms.txt").write_text("\n".join(lines), encoding="utf-8")

    full = [LLMS_HEADER]
    for page in ["README.md"] + pages:
        text = (DOCS / page).read_text(encoding="utf-8")
        lines_ = text.split("\n")
        if lines_ and LANG_SWITCH.match(lines_[0]):
            text = "\n".join(lines_[1:]).lstrip("\n")
        full.append(f"\n\n<!-- source: {RAW}docs/{page} -->\n\n{text}")
    (dest / "llms-full.txt").write_text("".join(full), encoding="utf-8")


if __name__ == "__main__":
    main()
