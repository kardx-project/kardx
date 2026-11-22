package com.example.restdocs.coupon;

import java.time.LocalDate;

/**
 * Java record 응답. primitive 컴포넌트도 자동 추출된다.
 * {@code usable} 은 {@code @ApiField} 대신 아래 Javadoc {@code @param} 에서 설명을 읽는다.
 *
 * @param usable 사용 가능 여부
 */
public record CouponResponse(
        @ApiField("쿠폰 코드") String code,
        @ApiField("할인율 (%)") int discountRate,
        @ApiField("만료일") LocalDate expiresAt,
        boolean usable) {
}
