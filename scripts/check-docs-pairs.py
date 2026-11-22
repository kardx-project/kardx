"""Check that the English and Korean docs stay in step.

python3 scripts/check-docs-pairs.py [--root DIR]

Pairs: README.md <-> README.ko.md, CONTRIBUTING.md <-> CONTRIBUTING.ko.md (when either exists),
docs/*.md <-> docs/ko/*.md. For every pair it compares
  - the file sets (a doc present in only one tree),
  - heading structure: same number and levels of headings in the same order (titles are not compared),
  - fenced code blocks: same count, same language tags, same code. Comments are dropped, and on the Korean
    side every run of text containing Hangul becomes a wildcard, so translated comments, string literals and
    labels match; everything else must be identical line by line,
  - tables: same count, and per table the same number of rows and columns,
  - relative link targets: the same set of files (anchors and the ko/ path difference ignored).

A difference that is intended is marked in either doc with an HTML comment directly above the code block or
table (only blank lines may come between):
  <!-- docs-pair: ignore-next-block -->   do not compare the code of the next fenced block
  <!-- docs-pair: ignore-next-table -->   do not compare the shape of the next table

Output: one `path:line: message` per finding, then a summary line. Exit code 1 when there are findings.
"""
import argparse
import os
import re
import sys

HANGUL = re.compile(r'[가-힣ㄱ-ㆎ]')
# A run of text without code delimiters that contains Hangul: a translated word, phrase or label
HANGUL_SPAN = re.compile(r'[^\[\]{}()"\'`|<>=,;:]*[가-힣ㄱ-ㆎ][^\[\]{}()"\'`|<>=,;:]*')
SENTENCE_GAP = re.compile(r'[\s,;:().·/!?-]*')
FENCE = re.compile(r'^(\s*)(```+|~~~+)\s*([\w+-]*)')
HEADING = re.compile(r'^(#{1,6})\s+\S')
INLINE_LINK = re.compile(r'!?\[(?:[^\[\]]|\[[^\]]*\])*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)')
REF_LINK = re.compile(r'^\s{0,3}\[[^\]]+\]:\s*<?(\S+?)>?(?:\s+.*)?$')
MARKER = re.compile(r'<!--\s*docs-pair:\s*([\w-]+)\s*-->')
MARKERS = {'ignore-next-block', 'ignore-next-table'}
HASH_COMMENT_LANGS = {'yaml', 'yml', 'properties', 'bash', 'sh', 'shell', 'zsh', 'toml', 'ini', 'dockerfile'}
SLASH_COMMENT_LANGS = {'kotlin', 'kt', 'kts', 'java', 'groovy', 'gradle', 'js', 'javascript', 'ts', 'typescript',
                       'json5', 'asciidoc', 'adoc', 'scala', 'c', 'cpp', 'go', 'rust', 'swift'}
BLOCK_COMMENT_LANGS = SLASH_COMMENT_LANGS - {'asciidoc', 'adoc'}
# Other English/Korean pairs at the repository root, checked when either file exists
ROOT_PAIRS = [('CONTRIBUTING.md', 'CONTRIBUTING.ko.md')]


def closes(line, mark):
    """line 이 mark 로 연 코드 블록을 닫는 펜스인지. 같은 문자로 같거나 더 길게, 뒤에는 공백만 온다."""
    stripped = line.strip()
    run = len(stripped) - len(stripped.lstrip(mark[0]))
    return run >= len(mark) and not stripped[run:].strip()


class Block:
    def __init__(self, line, lang, lines, ignored):
        self.line, self.lang, self.lines, self.ignored = line, lang, lines, ignored


class Table:
    def __init__(self, line, rows, cols, ignored):
        self.line, self.rows, self.cols, self.ignored = line, rows, cols, ignored


class Doc:
    """Headings, code blocks, tables and links of one Markdown file, each with its 1-based line number."""

    def __init__(self, path, rel):
        self.path, self.rel = path, rel
        self.headings, self.blocks, self.tables, self.links = [], [], [], {}
        self.bad_markers = []
        with open(path, encoding='utf-8') as f:
            self._parse(f.read().splitlines())

    def _parse(self, lines):
        pending = {}  # marker name -> line. A marker applies to the block or table right below it
        i = 0
        while i < len(lines):
            line = lines[i]
            markers = list(MARKER.finditer(line))
            for m in markers:
                if m.group(1) in MARKERS:
                    pending[m.group(1)] = i + 1
                else:
                    self.bad_markers.append((i + 1, f'unknown marker docs-pair: {m.group(1)} '
                                                    f'(known: {", ".join(sorted(MARKERS))})'))
            if markers or not line.strip():
                i += 1
                continue
            fence = FENCE.match(line)
            table = not fence and line.lstrip().startswith('|')
            for name, number in pending.items():
                if not (fence and name == 'ignore-next-block' or table and name == 'ignore-next-table'):
                    self.bad_markers.append((number, f'marker docs-pair: {name} is not directly above a '
                                                     f'{"code block" if name == "ignore-next-block" else "table"}'))
            ignored, pending = set(pending), {}
            if fence:
                indent, mark, lang = fence.groups()
                start, body = i + 1, []
                i += 1
                # 여는 펜스와 같은 문자로, 길이가 같거나 긴 펜스만 블록을 닫는다 (CommonMark). ```` 안의 ``` 는 본문이다
                while i < len(lines) and not closes(lines[i], mark):
                    body.append(lines[i][len(indent):] if lines[i].startswith(indent) else lines[i].lstrip())
                    i += 1
                self.blocks.append(Block(start, lang.lower(), body, 'ignore-next-block' in ignored))
                i += 1
                continue
            if table:
                start, rows = i + 1, []
                while i < len(lines) and lines[i].lstrip().startswith('|'):
                    rows.append(lines[i])
                    i += 1
                self._table(start, rows, 'ignore-next-table' in ignored)
                continue
            if HEADING.match(line):
                self.headings.append((i + 1, len(HEADING.match(line).group(1))))
            self._links(i + 1, line)
            i += 1
        for name, number in pending.items():
            self.bad_markers.append((number, f'marker docs-pair: {name} is not followed by anything'))

    def _table(self, line, rows, ignored):
        cells = [cell_count(row) for row in rows]
        separator = [n for n, row in enumerate(rows) if re.fullmatch(r'\s*\|?[\s:|-]+\|?\s*', row)]
        body = len(rows) - len(separator)
        self.tables.append(Table(line, body, max(cells, default=0), ignored))
        for n, row in enumerate(rows):
            self._links(line + n, row)

    def _links(self, number, line):
        targets = [m.group(1) for m in INLINE_LINK.finditer(strip_inline_code(line))]
        ref = REF_LINK.match(line)
        if ref:
            targets.append(ref.group(1))
        for target in targets:
            normalized = self._normalize(target)
            if normalized:
                self.links.setdefault(normalized, number)

    def _normalize(self, target):
        """Repository path of a relative link with the Korean tree mapped onto the English one. None for others."""
        if re.match(r'^[a-z][\w+.-]*:', target, re.I) or target.startswith(('#', '/')):
            return None
        file = target.split('#', 1)[0].split('?', 1)[0]
        if not file:
            return None
        resolved = os.path.normpath(os.path.join(os.path.dirname(self.rel), file)).replace(os.sep, '/')
        return english_path(resolved)


def strip_inline_code(line):
    return re.sub(r'(`+)[^`]*?\1', '``', line)


def cell_count(row):
    """Columns of a table row: pipes outside inline code and not escaped."""
    text = strip_inline_code(row.strip()).replace('\\|', '')
    if text.startswith('|'):
        text = text[1:]
    if text.endswith('|'):
        text = text[:-1]
    return text.count('|') + 1


def english_path(rel):
    for en_name, ko_name in [('README.md', 'README.ko.md')] + ROOT_PAIRS:
        if rel == ko_name:
            return en_name
    if rel.startswith('docs/ko/'):
        return 'docs/' + rel[len('docs/ko/'):]
    return rel


def code_lines(block):
    """Non-blank lines of a block with comments removed and whitespace collapsed, as (offset, text)."""
    out, in_block_comment = [], False
    for offset, raw in enumerate(block.lines):
        text = raw
        if block.lang in BLOCK_COMMENT_LANGS:
            if in_block_comment:
                if '*/' not in text:
                    continue
                text, in_block_comment = text.split('*/', 1)[1], False
            text = re.sub(r'/\*.*?\*/', '', text)
            if '/*' in text:
                text, in_block_comment = text.split('/*', 1)[0], True
        if block.lang in SLASH_COMMENT_LANGS:
            text = re.sub(r'(^|\s)//.*$', '', text)
        if block.lang in HASH_COMMENT_LANGS:
            text = re.sub(r'(^|\s)#.*$', '', text)
        if block.lang in ('xml', 'html'):
            text = re.sub(r'<!--.*?-->', '', text)
        if block.lang == 'mermaid':
            text = re.sub(r'^\s*%%.*$', '', text)
        text = ' '.join(text.split())
        if text:
            out.append((offset, text))
    return out


def korean_pattern(text):
    """Regex for a Korean code line: every Hangul run (with its words) matches any text.

    Runs separated only by punctuation are one sentence (`안내 (선택, _ 로 …)`) and become one wildcard,
    because the English sentence puts its punctuation elsewhere."""
    spans = []
    for m in HANGUL_SPAN.finditer(text):
        if spans and SENTENCE_GAP.fullmatch(text[spans[-1][1]:m.start()]):
            spans[-1] = (spans[-1][0], m.end())
        else:
            spans.append((m.start(), m.end()))
    parts, last = [], 0
    for start, end in spans:
        parts.append(re.escape(text[last:start]))
        parts.append(r'.*?')
        last = end
    parts.append(re.escape(text[last:]))
    return re.compile(''.join(parts))


def same_line(english, korean):
    if english == korean:
        return True
    if not HANGUL.search(korean):
        return False
    return korean_pattern(korean).fullmatch(english) is not None


class Report:
    def __init__(self):
        self.findings = []

    def add(self, rel, line, message):
        self.findings.append(f'{rel}:{line}: {message}')


def compare(en, ko, report):
    if [h[1] for h in en.headings] != [h[1] for h in ko.headings]:
        for n, (e, k) in enumerate(zip(en.headings, ko.headings)):
            if e[1] != k[1]:
                report.add(ko.rel, k[0], f'heading #{n + 1} is level {k[1]}, but level {e[1]} in {en.rel}:{e[0]}')
                break
        else:
            longer, shorter = (en, ko) if len(en.headings) > len(ko.headings) else (ko, en)
            extra = longer.headings[len(shorter.headings)]
            report.add(longer.rel, extra[0], f'{len(longer.headings)} headings, but {len(shorter.headings)} in '
                                             f'{shorter.rel} (first extra heading here)')

    if len(en.blocks) != len(ko.blocks):
        longer, shorter = (en, ko) if len(en.blocks) > len(ko.blocks) else (ko, en)
        report.add(longer.rel, longer.blocks[len(shorter.blocks)].line,
                   f'{len(longer.blocks)} code blocks, but {len(shorter.blocks)} in {shorter.rel}')
    for n, (e, k) in enumerate(zip(en.blocks, ko.blocks)):
        if e.lang != k.lang:
            report.add(ko.rel, k.line, f'code block #{n + 1} is `{k.lang or "(none)"}`, '
                                       f'but `{e.lang or "(none)"}` in {en.rel}:{e.line}')
            break
        if e.ignored or k.ignored:
            continue
        compare_block(n, en, e, ko, k, report)

    if len(en.tables) != len(ko.tables):
        longer, shorter = (en, ko) if len(en.tables) > len(ko.tables) else (ko, en)
        report.add(longer.rel, longer.tables[len(shorter.tables)].line,
                   f'{len(longer.tables)} tables, but {len(shorter.tables)} in {shorter.rel}')
    for n, (e, k) in enumerate(zip(en.tables, ko.tables)):
        if e.ignored or k.ignored:
            continue
        if (e.rows, e.cols) != (k.rows, k.cols):
            report.add(ko.rel, k.line, f'table #{n + 1} has {k.rows} rows x {k.cols} columns, '
                                       f'but {e.rows} x {e.cols} in {en.rel}:{e.line}')

    for target in sorted(set(en.links) - set(ko.links)):
        report.add(en.rel, en.links[target], f'links to {target}, but {ko.rel} does not')
    for target in sorted(set(ko.links) - set(en.links)):
        report.add(ko.rel, ko.links[target], f'links to {target}, but {en.rel} does not')

    for doc in (en, ko):
        for line, message in doc.bad_markers:
            report.add(doc.rel, line, message)


def compare_block(n, en, e, ko, k, report):
    en_lines, ko_lines = code_lines(e), code_lines(k)
    if len(en_lines) != len(ko_lines):
        report.add(ko.rel, k.line, f'code block #{n + 1} has {len(ko_lines)} code lines, '
                                   f'but {len(en_lines)} in {en.rel}:{e.line}')
        return
    for (eo, et), (ko_offset, kt) in zip(en_lines, ko_lines):
        if not same_line(et, kt):
            report.add(ko.rel, k.line + 1 + ko_offset,
                       f'code block #{n + 1} differs from {en.rel}:{e.line + 1 + eo}: `{kt}` vs `{et}`')
            return


def pairs(root):
    out = [('README.md', 'README.ko.md')]
    for en_name, ko_name in ROOT_PAIRS:
        if os.path.isfile(os.path.join(root, en_name)) or os.path.isfile(os.path.join(root, ko_name)):
            out.append((en_name, ko_name))
    docs, ko = os.path.join(root, 'docs'), os.path.join(root, 'docs', 'ko')
    names = lambda d: {n for n in os.listdir(d) if n.endswith('.md')} if os.path.isdir(d) else set()
    en_names, ko_names = names(docs), names(ko)
    for name in sorted(en_names | ko_names):
        out.append((f'docs/{name}', f'docs/ko/{name}'))
    return out


def main():
    parser = argparse.ArgumentParser(
        description='Check that README.md/CONTRIBUTING.md/docs/*.md (English) and README.ko.md/CONTRIBUTING.ko.md/'
                    'docs/ko/*.md (Korean) stay in step: '
                    'file sets, heading levels, code blocks, table shapes and relative links.',
        epilog='Mark an intended difference with <!-- docs-pair: ignore-next-block --> or '
               '<!-- docs-pair: ignore-next-table --> just before the block or table in either doc. '
               'Exit code: 0 in step, 1 findings.')
    parser.add_argument('--root', default=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'),
                        help='repository root (default: the parent of this script)')
    args = parser.parse_args()
    root = os.path.abspath(args.root)
    report, checked = Report(), 0
    for en_rel, ko_rel in pairs(root):
        en_path, ko_path = os.path.join(root, en_rel), os.path.join(root, ko_rel)
        if not os.path.isfile(en_path) or not os.path.isfile(ko_path):
            present, missing = (en_rel, ko_rel) if os.path.isfile(en_path) else (ko_rel, en_rel)
            report.add(present, 1, f'no counterpart {missing}')
            continue
        compare(Doc(en_path, en_rel), Doc(ko_path, ko_rel), report)
        checked += 1
    for finding in report.findings:
        print(finding)
    status = 'in step' if not report.findings else f'{len(report.findings)} finding(s)'
    print(f'docs pairs: {checked} pair(s) checked, {status}')
    sys.exit(1 if report.findings else 0)


if __name__ == '__main__':
    main()
