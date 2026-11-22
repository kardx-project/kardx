package io.github.kardx.internal.asciidoc.invalid

import io.github.kardx.annotation.EnumCode

@EnumCode("오타", descriptionProperty = "descripton")
enum class TypoStatus(val description: String) { A("에이"), }
