package io.github.kardx.kotest

/**
 * 리프 테스트 한 번의 결과. 실패한 테스트의 스니펫은 지우고, 성공한 테스트만 문서에 넣는다.
 * 건너뛴 테스트는 실패도 성공도 아니다.
 *
 * @param errorOrFailure Kotest 결과의 `isErrorOrFailure`. 결과가 없으면(취소 등) null 이고 실패로 본다
 * @param success Kotest 결과의 `isSuccess`. 결과가 없으면 null 이고 성공이 아니다
 */
internal class TestOutcome(errorOrFailure: Boolean?, success: Boolean?) {
    val failed: Boolean = errorOrFailure ?: true
    val succeeded: Boolean = success ?: false
}
