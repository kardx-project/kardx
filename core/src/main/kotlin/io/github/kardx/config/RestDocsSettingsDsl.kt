package io.github.kardx.config

/**
 * 설정 DSL 표시. `openApi { }` 안에서 바깥 블록(`RestDocsSettings`)의 멤버를 실수로 부르지 않게 막는다.
 * 바깥 값을 바꾸려면 `this@configure` 처럼 이름을 붙이거나 블록을 닫고 바꾼다.
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class RestDocsSettingsDsl
