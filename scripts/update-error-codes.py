"""Keep the error code tables in docs/troubleshooting.md and docs/ko/troubleshooting.md in step with RestDocsErrorCode.

python3 scripts/update-error-codes.py           rewrite the tables
python3 scripts/update-error-codes.py --check   fail (exit 1) when a table is out of date

The source is core/src/main/kotlin/io/github/kardx/diagnostics/RestDocsErrorCode.kt. Each entry becomes one row between
`<!-- error-codes -->` and `<!-- /error-codes -->`:

  | <a id="kdx-011"></a>`KDX-011` | problem | fix |

- the anchor is what `RestDocsErrorCode.docsUrl` links to (`troubleshooting.md#kdx-011`),
- the problem is the entry's English `summary` (English doc) or the first paragraph of its KDoc (Korean doc),
- the fix column is written by hand and kept as is. A new code gets an empty fix (`?`) and --check fails until it is
  filled in; a removed code's row disappears.
"""
import argparse
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ENUM = os.path.join(ROOT, 'core/src/main/kotlin/io/github/kardx/diagnostics/RestDocsErrorCode.kt')
DOCS = {
    'en': os.path.join(ROOT, 'docs/troubleshooting.md'),
    'ko': os.path.join(ROOT, 'docs/ko/troubleshooting.md'),
}
HEADERS = {
    'en': ['Code', 'Problem', 'Fix'],
    'ko': ['코드', '문제', '해결'],
}
START, END = '<!-- error-codes -->', '<!-- /error-codes -->'
MISSING = '?'
ENTRY = re.compile(r'/\*\*(?P<doc>(?:(?!\*/).)*?)\*/\s*(?P<name>[A-Z_]+)\("(?P<code>KDX-\d{3})",\s*"(?P<summary>[^"]+)"\)', re.S)
ROW = re.compile(r'^\|\s*<a id="(kdx-\d{3})"></a>.*?\|.*?\|\s*(?P<fix>.*?)\s*\|\s*$')
KDOC_LINK = re.compile(r'\[([^\]]+)\](?!\()')


def entries():
    """(code, English summary, Korean KDoc first paragraph) in source order."""
    with open(ENUM, encoding='utf-8') as f:
        text = f.read()
    found = []
    for match in ENTRY.finditer(text):
        found.append((match.group('code'), match.group('summary'), first_paragraph(match.group('doc'))))
    if not found:
        sys.exit(f'{ENUM}: no RestDocsErrorCode entries found')
    return found


def first_paragraph(doc):
    lines = [line.strip().lstrip('*').strip() for line in doc.strip().splitlines()]
    paragraph = []
    for line in lines:
        if not line and paragraph:
            break
        if line:
            paragraph.append(line)
    text = KDOC_LINK.sub(lambda m: '`' + m.group(1) + '`', ' '.join(paragraph))
    return text.replace('|', '\\|')


def kept_fixes(region):
    fixes = {}
    for line in region.splitlines():
        match = ROW.match(line)
        if match:
            fixes[match.group(1)] = match.group('fix')
    return fixes


def table(lang, codes, fixes):
    header = HEADERS[lang]
    rows = [
        '| ' + ' | '.join(header) + ' |',
        '|---|---|---|',
    ]
    for code, summary, korean in codes:
        anchor = code.lower()
        problem = summary if lang == 'en' else korean
        fix = fixes.get(anchor) or MISSING
        rows.append(f'| <a id="{anchor}"></a>`{code}` | {problem} | {fix} |')
    return '\n'.join(rows)


def updated(path, lang, codes):
    with open(path, encoding='utf-8') as f:
        text = f.read()
    start, end = text.find(START), text.find(END)
    if start < 0 or end < start:
        sys.exit(f'{path}: markers {START} ... {END} not found')
    region = text[start + len(START):end]
    body = table(lang, codes, kept_fixes(region))
    return text, text[:start + len(START)] + '\n' + body + '\n' + text[end:]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true', help='fail when a table is out of date or a fix is missing')
    args = parser.parse_args()
    codes = entries()
    problems = []
    for lang, path in DOCS.items():
        before, after = updated(path, lang, codes)
        missing = [code for code, _, _ in codes if f'<a id="{code.lower()}"></a>`{code}` |' in after
                   and re.search(rf'`{code}` \|[^\n]*\| \{re.escape(MISSING)} \|$', after, re.M)]
        if args.check:
            if before != after:
                problems.append(f'{os.path.relpath(path, ROOT)}: error code table is out of date '
                                f'(run python3 scripts/update-error-codes.py)')
        elif before != after:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(after)
        if missing:
            problems.append(f'{os.path.relpath(path, ROOT)}: no fix written for {", ".join(missing)}')
    for problem in problems:
        print(problem)
    print(f'{len(codes)} error codes, {len(problems)} problem(s)')
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
