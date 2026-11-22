package io.github.kardx.dsl

import io.github.kardx.ExperimentalRestDocsApi
/**
 * 설명 자리에 두면 핸들러 메서드 KDoc·Javadoc 의 `@param` 설명을 쓴다. 이름만 적는 버전(`queryParameters("page")`)과 같지만
 * 수식어를 붙일 수 있다.
 *
 * ```kotlin
 * queryParameters(
 *     "page" queryMeans FromParamTag isOptional true example "0",
 *     "keyword" queryMeans "검색어",
 * )
 * requestHeaders("Accept-Language" headerMeans FromParamTag isOptional true withDefaultValue "ko")
 * ```
 *
 * [pathMeans]·[queryMeans]·[formMeans]·[headerMeans]·[partMeans] 에 쓸 수 있다. 테스트에 적은 설명이 있으면 그 설명을 쓰고,
 * 핸들러를 모르거나 `@param` 이 없으면 테스트가 실패한다. `@param` 은 요청 항목만 설명하므로 응답 헤더·메시지 헤더에는 쓸 수 없다.
 *
 * **실험 기능.** `@param` 색인은 Gradle 플러그인의 KDoc 기능이 만들며 기본으로 꺼져 있다
 * (`kardx { sourceDocs { enabled = true } }`, `paramDocs` 기본 true).
 */
@ExperimentalRestDocsApi
public object FromParamTag
