package io.github.kardx.tooling.internal.check;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** {@code apiDocsCheck} 가 콘솔과 보고서 파일에 남기는, 기준 스펙과의 차이 요약. */
public final class OpenApiChangeReport {
    private static final String BULLET = "  - ";

    private OpenApiChangeReport() {
    }

    /**
     * 사람이 읽는 요약. 항목은 [limit] 개까지. 경고가 없으면 경고 절을 넣지 않는다. 첫 줄은 구조 차이가 없으면
     * {@code The OpenAPI spec matches the baseline (…)}, 있으면 {@code The OpenAPI spec differs from the baseline (…)}.
     */
    public static String summary(File baseline, File generated, List<String> breaking, List<String> warnings,
        List<String> differences, int limit) {
        List<String> lines = new ArrayList<>();

        String verdict = differences.isEmpty() ? "matches" : "differs from";

        lines.add("The OpenAPI spec " + verdict + " the baseline (baseline " + baseline + " ↔ generated " + generated
            + ")");
        lines.add("Breaking changes: " + breaking.size());
        append(lines, breaking, limit);

        if (!warnings.isEmpty()) {
            lines.add("Warnings: " + warnings.size());
            append(lines, warnings, limit);
        }

        lines.add("All structural differences: " + differences.size());
        append(lines, differences, limit);

        return String.join("\n", lines);
    }

    private static void append(List<String> lines, List<String> items, int limit) {
        items.stream()
            .limit(limit)
            .forEach(item -> lines.add(BULLET + item));

        if (items.size() > limit) {
            lines.add(BULLET + "… and " + (items.size() - limit) + " more (see the report)");
        }
    }
}
