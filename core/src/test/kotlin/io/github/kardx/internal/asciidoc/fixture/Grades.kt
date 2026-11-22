package io.github.kardx.internal.asciidoc.fixture

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.github.kardx.annotation.EnumCode

/** 기본값: 코드는 상수 이름(JSON 값), 설명은 `description`. */
@EnumCode("회원 등급")
enum class MemberGrade(val type: String, val description: String) { GOLD("G", "골드"), SILVER("S", "실버") }

/** 명시 지정: 코드는 getter, 설명은 필드. `@Deprecated` 상수는 빠진다. */
@EnumCode("결제 상태", codeProperty = "code", descriptionProperty = "label")
enum class PaymentStatus(val code: Int, val label: String) {
    PAID(10, "결제 완료"),

    @Deprecated("use PAID")
    DONE(20, "완료"),
}

/** 코드 기본값은 `@JsonValue` 값. */
@EnumCode("배송 상태")
enum class DeliveryStatus(@get:JsonValue val wire: String, val description: String) {
    READY("ready", "준비"),
    SHIPPED("shipped", "발송"),
}

/** 코드 기본값은 상수의 `@JsonProperty` 값. 설명 프로퍼티가 없으면 상수 이름. */
@EnumCode("정렬 방향")
enum class SortDirection {
    @JsonProperty("asc")
    ASC,

    @JsonProperty("desc")
    DESC,
}

/** 설정 `enumDescriptionProperty`(label) 가 `description` 보다 먼저다. */
@EnumCode("라벨 우선")
enum class Labeled(val label: String, val description: String) { ON("켜짐", "설명-켜짐"), }

/** 상수 이름을 코드로 적고 싶으면 `name` (Enum.name()). */
@EnumCode("이름 코드", codeProperty = "name")
enum class NamedCode(@get:JsonValue val wire: String) { FIRST("first"), }

enum class NotDocumented { A }
