package io.github.kardx.kotest

/**
 * 리프 테스트 하나의 이름 정보. [SectionTitleProvider]·[SnippetNameProvider] 가 받는다. 라이브러리가 만들어 넘기며, 앞으로
 * 정보가 더해질 수 있다.
 *
 * @property names 바깥 컨테이너에서 리프 테스트까지의 이름 (스타일 접두사 제외, `["사용자가 있을 때", "단건 조회하면", "…"]`)
 * @property specClass 스펙 클래스
 */
public class TestPath internal constructor(public val names: List<String>, public val specClass: Class<*>) {
    override fun toString(): String = "TestPath(${specClass.simpleName}: ${names.joinToString(" / ")})"
}
