package io.github.kardx.dsl

/**
 * 문서화 DSL 범위 표시. 바깥 빌더의 함수를 안쪽에서 실수로 부르지 않게 한다.
 *
 * REST 문서화 블록(`andDocument { ... }`)과 메시지 문서화 블록(`docs.send(...) { ... }`)이 함께 쓴다.
 */
@DslMarker
public annotation class RestDocsDsl
