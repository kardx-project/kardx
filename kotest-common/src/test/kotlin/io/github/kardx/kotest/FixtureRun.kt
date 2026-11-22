package io.github.kardx.kotest

import java.io.File

/**
 * 고정 스펙을 Kotest 엔진으로 실행한 결과와 출력 디렉터리. 실행(`runFixtures`)은 Kotest 버전마다 런처 API 가 달라 각 모듈의
 * 테스트 소스에 있다.
 *
 * @property results 리프·컨테이너 테스트 이름 → 결과
 */
class FixtureRun(val snippets: File, val asciidoc: File, val results: Map<String, FixtureResult>) {
    fun document(name: String): File = File(asciidoc, "$name.adoc")

    fun snippet(spec: String, name: String): File = File(snippets, "$spec/$name")
}

/** 테스트 하나의 결과. Kotest 버전마다 결과 클래스의 패키지가 달라 필요한 판정만 옮겨 둔다. */
class FixtureResult(val isSuccess: Boolean, val isErrorOrFailure: Boolean)

/** 섹션 앵커와 제목 두 줄 (`[[anchor]]` + `== icon:link[] 제목`). */
fun section(anchor: String, title: String) = "[[$anchor]]\n== icon:link[] $title"
