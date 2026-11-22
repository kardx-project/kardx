package io.github.kardx.internal.documentation

/**
 * 테스트가 실제로 받은 상태와 같은 오류 응답 선언을 뺀다. 그 상태는 실제 응답이 이미 문서화하므로 선언이 겹칠 뿐이다.
 * `error-responses` 스니펫과 `openapi-extras.json` 의 `errorResponses` 에 모두 나가지 않는다 (오류·경고 없음).
 *
 * @param status 테스트가 받은 응답 상태
 */
internal fun DocumentationSpec.withoutReceivedErrorResponse(status: Int): DocumentationSpec {
    if (errorResponses.none { it.status == status }) return this

    return copy(errorResponses = errorResponses.filterNot { it.status == status })
}
