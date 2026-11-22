package io.github.kardx.internal.asciidoc.titles

import io.github.kardx.annotation.EnumCode

@EnumCode("입금/출금")
enum class TransferType(val description: String) { DEPOSIT("입금"), }

/** 같은 제목이 둘이면 뒤의 것에 클래스 이름이 붙는다 */
@EnumCode("입금 / 출금")
enum class TransferKind(val description: String) { WITHDRAW("출금"), }
