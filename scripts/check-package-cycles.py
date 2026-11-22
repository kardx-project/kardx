#!/usr/bin/env python3
"""소스의 import 로 패키지 의존 그래프를 만들고 순환이 있는지 검사한다.

사용법: python3 scripts/check-package-cycles.py [--graph] [소스 디렉터리 ...]
디렉터리를 주지 않으면 core(main), kotest(main, Kotest 6.x), kotest5(main, Kotest 5.x), Gradle 플러그인(main),
tooling(main), Maven 플러그인(main)을 각각 검사한다.
한 모듈의 소스가 여러 디렉터리에 있으면 `+` 로 이어 한 단위로 검사한다 (kotest·kotest5 는 공유 소스 kotest-common 과 함께).
--graph 를 주면 패키지별 의존과 계층(아무것에도 기대지 않는 패키지가 0)을 출력한다.
순환이 하나라도 있으면 종료 코드 1 로 끝난다.

간선은 같은 저장소 패키지(io.github.kardx 아래)를 가리키는 import 와 코드에 쓴 전체 이름(FQN)이다.
주석과 문자열 안의 이름은 세지 않는다.
"""
import os
import re
import sys
from collections import defaultdict

ROOT_PACKAGE = "io.github.kardx"
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SOURCES = [
    "core/src/main/kotlin",
    "kotest/src/main/kotlin+kotest-common/src/main/kotlin",
    "kotest5/src/main/kotlin+kotest-common/src/main/kotlin",
    "gradle-plugin/src/main/java",
    "tooling/src/main/java",
    "maven-plugin/src/main/java",
]

COMMENTS = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)
STRINGS = re.compile(r'"""(?:.|\n)*?"""|"(?:\\.|[^"\\\n])*"')
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)", re.M)
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)", re.M)
QUALIFIED = re.compile(r"\b" + re.escape(ROOT_PACKAGE) + r"(?:\.\w+)+")


def strip(source):
    return STRINGS.sub('""', COMMENTS.sub("", source))


def read_sources(directory):
    """파일마다 (패키지, 코드) 를 돌려준다."""
    for current, _, names in os.walk(directory):
        for name in names:
            if name.endswith((".kt", ".java")):
                with open(os.path.join(current, name), encoding="utf-8") as f:
                    code = strip(f.read())
                match = PACKAGE.search(code)
                if match:
                    yield match.group(1), code


def owning_package(name, packages):
    """name(클래스·함수의 전체 이름)이 속한 가장 긴 패키지."""
    parts = name.split(".")
    for end in range(len(parts), 0, -1):
        candidate = ".".join(parts[:end])
        if candidate in packages:
            return candidate
    return None


def build_graph(directories):
    files = [source for directory in directories for source in read_sources(directory)]
    packages = {package for package, _ in files}
    graph = defaultdict(set)
    for package, code in files:
        graph[package]
        targets = IMPORT.findall(code) + QUALIFIED.findall(IMPORT.sub("", code))
        for target in targets:
            owner = owning_package(target, packages)
            if owner and owner != package:
                graph[package].add(owner)
    return graph


def cycles(graph):
    """강하게 연결된 요소 중 패키지가 둘 이상인 것 (Tarjan)."""
    index, low, stack, on_stack, found = {}, {}, [], set(), []
    counter = [0]

    def visit(node):
        index[node] = low[node] = counter[0]
        counter[0] += 1
        stack.append(node)
        on_stack.add(node)
        for target in graph[node]:
            if target not in index:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in on_stack:
                low[node] = min(low[node], index[target])
        if low[node] == index[node]:
            component = []
            while True:
                member = stack.pop()
                on_stack.discard(member)
                component.append(member)
                if member == node:
                    break
            if len(component) > 1:
                found.append(sorted(component))

    sys.setrecursionlimit(10000)
    for node in sorted(graph):
        if node not in index:
            visit(node)
    return found


def levels(graph):
    """계층 번호. 의존하는 패키지가 없으면 0, 아니면 의존 대상 중 가장 높은 계층 + 1."""
    result = {}

    def level(node):
        if node not in result:
            result[node] = 0
            result[node] = max((level(target) + 1 for target in graph[node]), default=0)
        return result[node]

    for node in graph:
        level(node)
    return result


def short(package):
    return package[len(ROOT_PACKAGE) + 1:] if package.startswith(ROOT_PACKAGE + ".") else package


def main(arguments):
    show_graph = "--graph" in arguments
    directories = [a for a in arguments if not a.startswith("--")] or DEFAULT_SOURCES
    failed = False
    for directory in directories:
        graph = build_graph([os.path.join(REPO, part) for part in directory.split("+")])
        found = cycles(graph)
        edges = sum(len(targets) for targets in graph.values())
        print(f"{directory}: 패키지 {len(graph)}개, 의존 {edges}개, 순환 {len(found)}개")
        for component in found:
            failed = True
            print("  CYCLE " + " <-> ".join(short(p) for p in component))
        if show_graph and not found:
            for package, level in sorted(levels(graph).items(), key=lambda item: (item[1], item[0])):
                targets = ", ".join(sorted(short(t) for t in graph[package])) or "-"
                print(f"  L{level} {short(package)} -> {targets}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
