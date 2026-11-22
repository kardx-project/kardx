package io.github.kardx.internal.asciidoc.invalidcode

import io.github.kardx.annotation.EnumCode

@EnumCode("코드 오타", codeProperty = "cod")
enum class CodeTypo(val code: String) { A("a"), }
