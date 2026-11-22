package io.github.kardx.tooling.cli;

import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.PrintStream;

/** 명령줄 로그: 진행 메시지는 표준 출력, 경고는 표준 오류. info 는 {@code --verbose} 일 때만, debug 는 쓰지 않는다. */
final class ConsoleLog implements ToolLog {
    private final PrintStream out;
    private final PrintStream err;
    private final boolean verbose;

    ConsoleLog(PrintStream out, PrintStream err, boolean verbose) {
        this.out = out;
        this.err = err;
        this.verbose = verbose;
    }

    @Override
    public void lifecycle(String format, Object... args) {
        out.println(ToolLog.format(format, args));
    }

    @Override
    public void info(String format, Object... args) {
        if (verbose) {
            out.println(ToolLog.format(format, args));
        }
    }

    @Override
    public void warn(String format, Object... args) {
        err.println("warning: " + ToolLog.format(format, args));
    }

    @Override
    public void debug(String format, Object... args) {
        // 명령줄에서는 쓰지 않는다 (건너뛴 소스 파일 같은 세부 사항)
    }
}
