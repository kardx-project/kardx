"""docs/*.md(영문)와 docs/ko/*.md(한국어) 머리에 그 문서의 목차를 넣는다. 문서를 고친 뒤 다시 실행한다.

python3 scripts/update-docs-toc.py

목차는 문서 목록 링크 줄(영문 `[Document list](README.md)`, 한국어 `[문서 목록](README.md)`) 바로 아래에 들어간다.
영문 문서는 README.md 로 가는 첫 링크 줄을 위 형태로 맞춘다. 아직 없는 문서는 건너뛴다.
"""
import importlib.util
import os
import re

DOCS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'docs')
START, END = '<!-- toc -->', '<!-- /toc -->'

# (디렉터리, 목록 링크 줄, 목차 제목)
TREES = [
    (DOCS, '[Document list](README.md)', '**Contents**'),
    (os.path.join(DOCS, 'ko'), '[문서 목록](README.md)', '**목차**'),
]
NAV_LINE = re.compile(r'^\[[^\]\n]+\]\(README\.md\)[ \t]*$', re.M)


def _pairs_checker():
    """코드 블록 규칙(들여쓴 펜스, ~~~, 더 긴 닫는 펜스)을 check-docs-pairs.py 와 같게 쓰려고 그 모듈을 읽는다."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'check-docs-pairs.py')
    spec = importlib.util.spec_from_file_location('check_docs_pairs', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


PAIRS = _pairs_checker()


def with_code_flags(lines):
    """(줄, 코드 블록 안인지) 목록. 여는·닫는 펜스 줄도 코드로 본다."""
    out, mark = [], None
    for line in lines:
        if mark is None:
            fence = PAIRS.FENCE.match(line)
            if fence:
                mark = fence.group(2)
            out.append((line, mark is not None))
        else:
            out.append((line, True))
            if PAIRS.closes(line, mark):
                mark = None
    return out


def collapse_blank_lines(text):
    """코드 블록 밖에서만 연속한 빈 줄을 하나로 줄인다. 코드 안의 빈 줄은 그대로 둔다."""
    out, blank = [], False
    for line, code in with_code_flags(text.split('\n')):
        if not code and not line.strip():
            if blank:
                continue
            blank = True
        else:
            blank = False
        out.append(line)
    return '\n'.join(out)


def anchor(title, seen):
    """GitHub 와 같은 규칙: 소문자, 글자·숫자·공백·하이픈·밑줄만 남기고 공백은 하이픈. 같은 제목은 -1, -2 …"""
    base = re.sub(r'[^\w\- ]', '', title.strip().lower()).replace(' ', '-')
    count = seen.get(base, 0)
    seen[base] = count + 1
    return base if count == 0 else f'{base}-{count}'


def toc(text, heading):
    lines, seen = [], {}
    for line, code in with_code_flags(text.splitlines()):
        if code:
            continue
        m = re.match(r'(#{1,3}) (.+)', line)
        if not m:
            continue
        level, title = len(m.group(1)), m.group(2).strip()
        link = anchor(title, seen)
        if level == 1:
            continue
        indent = '  ' * (level - 2)
        lines.append(f'{indent}- [{title}](#{link})')
    if not lines:
        return ''
    return '\n'.join([START, heading, ''] + lines + [END])


def update(directory, nav, heading):
    if not os.path.isdir(directory):
        return
    for name in sorted(os.listdir(directory)):
        path = os.path.join(directory, name)
        if not name.endswith('.md') or name == 'README.md' or not os.path.isfile(path):
            continue
        text = open(path, encoding='utf-8').read()
        text = re.sub(re.escape(START) + r'.*?' + re.escape(END) + r'\n*', '', text, flags=re.S)
        text = NAV_LINE.sub(lambda _: nav, text, count=1)
        block = toc(text, heading)
        if block and nav + '\n' in text:
            text = text.replace(nav + '\n', nav + '\n\n' + block + '\n', 1)
        elif block:
            print('  no nav line, toc skipped:', os.path.relpath(path, DOCS))
            block = ''
        text = collapse_blank_lines(text)
        open(path, 'w', encoding='utf-8').write(text)
        rel = os.path.relpath(path, DOCS)
        print('toc', rel, len([line for line in block.splitlines() if line.lstrip().startswith('- [')]))


for tree in TREES:
    update(*tree)
