package io.github.kardx.tooling.internal.sourcedocs;

import java.util.Locale;

/** 읽는 소스 언어. 파일 확장자로 정한다. */
enum SourceLanguage {
    KOTLIN,
    JAVA;

    /** @return 확장자가 {@code .kt}·{@code .java} 가 아니면 null */
    static SourceLanguage of(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);

        if (name.endsWith(".kt")) {
            return KOTLIN;
        }

        return name.endsWith(".java") ? JAVA : null;
    }
}
