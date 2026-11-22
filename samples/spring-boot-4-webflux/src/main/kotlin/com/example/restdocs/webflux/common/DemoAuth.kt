package com.example.restdocs.webflux.common

/**
 * 샘플 코드: 이벤트 스트림(SSE)의 데모용 인증. 고정 토큰 [TOKEN] 하나만 받는다. 실제 앱에서 쓰지 않는다.
 *
 * 토큰 값은 AsyncAPI 문서 설명(build 파일의 `asyncApi.description`)에도 적어 두어, 문서 뷰어의 연결 시험에서 그대로 넣을 수 있다.
 * `Authorization: Bearer demo-token` 헤더, 또는 헤더를 붙일 수 없는 브라우저(EventSource)용 쿼리 `?access_token=demo-token`.
 */
object DemoAuth {
    /** 데모 토큰. */
    const val TOKEN = "demo-token"

    /** `Authorization` 헤더 값. */
    const val BEARER = "Bearer $TOKEN"

    /** 브라우저(EventSource)가 토큰을 보내는 쿼리 파라미터 이름. */
    const val QUERY_PARAMETER = "access_token"

    /** [authorization] 헤더나 [accessToken] 쿼리 값 중 하나가 데모 토큰이면 true. */
    fun accepts(authorization: String?, accessToken: String?): Boolean = authorization == BEARER || accessToken == TOKEN
}
