package io.github.kardx.internal.introspection

import io.github.kardx.annotation.Masked
import io.github.kardx.config.MaskedAnnotation
import io.github.kardx.config.RestDocsConfiguration

/** 항상 읽는 [@Masked][Masked]. */
private val MASKED = MaskedAnnotation.of(Masked::class, "value")

/**
 * 프로퍼티 값이 마스킹되어 나가는지. [@Masked][Masked] 를 먼저 보고, 지금 적용 중인 설정의
 * [io.github.kardx.config.MaskingSettings.annotations] 를 순서대로 본다.
 * 마스킹되면 규칙 설명(없으면 빈 문자열), 아니면 null. 추출한 본문 필드에
 * [io.github.kardx.dsl.MASKED_ATTRIBUTE] 로 남는다.
 */
internal fun maskingOf(annotations: List<Annotation>): String? = sequenceOf(MASKED)
    .plus(RestDocsConfiguration.current.masking.annotations)
    .firstNotNullOfOrNull { it.read(annotations) }
