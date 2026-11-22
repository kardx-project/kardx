package io.github.kardx.tooling.internal.steps;

import io.github.kardx.tooling.ToolingException;
import io.github.kardx.tooling.internal.check.OpenApiBreakingChanges;
import io.github.kardx.tooling.internal.check.OpenApiChangeReport;
import io.github.kardx.tooling.internal.check.OpenApiStructureDiff;
import io.github.kardx.tooling.internal.io.TextFiles;
import io.github.kardx.tooling.internal.io.ToolLog;
import io.github.kardx.tooling.internal.spec.OpenApiSpecFiles;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 생성한 OpenAPI 스펙을 기준 스펙(커밋한 파일)과 비교한다. 다르면 보고서를 쓰고 {@code failOn} 에 따라 실패하거나 경고한다. */
public final class OpenApiCheckStep {
    /** 차이가 하나라도 있으면 실패. */
    public static final String FAIL_ON_ANY = "any";
    /** 깨는 변경이 있을 때만 실패. */
    public static final String FAIL_ON_BREAKING = "breaking";
    /** 실패하지 않고 경고만. */
    public static final String FAIL_ON_NONE = "none";
    private static final int CONSOLE_LIMIT = 20;

    private OpenApiCheckStep() {
    }

    /**
     * @param failOnSetting 오류 메시지에 보일 {@code failOn} 설정 이름 ({@code kardx.openApiCheck.failOn})
     * @param baselineCommand 기준을 갱신하는 명령 ({@code ./gradlew apiDocsBaseline})
     * @param requireBaselineSetting 기준 없이 통과시키는 설정 ({@code kardx.openApiCheck.requireBaseline = false})
     */
    public record Settings(String failOn, boolean requireBaseline, String failOnSetting, String baselineCommand,
        String requireBaselineSetting) {
    }

    /**
     * @param specs 생성한 스펙 후보 (있는 첫 파일을 쓴다)
     * @throws ToolingException 설정에 따라 실패할 때. 메시지에 요약과 보고서 위치가 있다
     */
    public static void run(Set<File> specs, File baseline, File report, Settings settings, ToolLog log) {
        String failOn = settings.failOn();

        if (!List.of(FAIL_ON_ANY, FAIL_ON_BREAKING, FAIL_ON_NONE).contains(failOn)) {
            throw new ToolingException(settings.failOnSetting() + " must be any, breaking or none, got '"
                + failOn + "'");
        }

        File generated = OpenApiSpecFiles.existing(specs);

        if (!baseline.isFile()) {
            missingBaseline(baseline, report, settings, log);

            return;
        }

        Map<?, ?> before = document(baseline);
        Map<?, ?> after = document(generated);
        OpenApiBreakingChanges.Changes changes = OpenApiBreakingChanges.analyze(before, after);
        List<String> breaking = changes.breaking();
        List<String> differences = OpenApiStructureDiff.compare(before, after);

        TextFiles.write(report, OpenApiChangeReport.summary(baseline, generated, breaking, changes.warnings(),
            differences, Integer.MAX_VALUE) + "\n");

        if (differences.isEmpty()) {
            log.lifecycle("The OpenAPI spec matches the baseline ({})", baseline);

            return;
        }

        boolean fail = FAIL_ON_ANY.equals(failOn) || FAIL_ON_BREAKING.equals(failOn) && !breaking.isEmpty();
        String message = OpenApiChangeReport.summary(baseline, generated, breaking, changes.warnings(), differences,
            CONSOLE_LIMIT)
            + "\nReport: " + report.toPath().toUri()
            + "\nIf the changes are intended, run " + settings.baselineCommand()
            + " to update the baseline and commit it.";

        finish(message, fail, log);
    }

    /**
     * 기준 파일이 없을 때. 기준이 없으면 어떤 변경도 판정할 수 없으므로 {@code requireBaseline} 이면 {@code none} 이 아닌 한
     * 실패한다 (그렇지 않으면 {@code breaking} 검사가 기준을 만들 때까지 계속 통과한다).
     */
    private static void missingBaseline(File baseline, File report, Settings settings, ToolLog log) {
        String failOn = settings.failOn();
        boolean strict = settings.requireBaseline();
        String missing = "No OpenAPI baseline: " + baseline + "\n"
            + "  Run " + settings.baselineCommand() + " to save the current spec as the baseline and commit it.";

        if (strict && !FAIL_ON_NONE.equals(failOn)) {
            missing += "\n  To pass without a baseline, set " + settings.requireBaselineSetting() + ".";
        }

        TextFiles.write(report, missing + "\n");
        finish(missing, strict ? !FAIL_ON_NONE.equals(failOn) : FAIL_ON_ANY.equals(failOn), log);
    }

    private static void finish(String message, boolean fail, ToolLog log) {
        if (fail) {
            throw new ToolingException(message);
        }

        log.warn("{}", message);
    }

    private static Map<?, ?> document(File file) {
        Object parsed = OpenApiSpecFiles.read(file);

        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ToolingException("Not an OpenAPI document: " + file);
        }

        return map;
    }
}
