package io.github.kardx.gradle.internal;

import io.github.kardx.tooling.internal.io.ToolLog;
import org.gradle.api.logging.Logger;

/** tooling 의 로그를 태스크 로거로 보낸다. 형식({@code {}})은 Gradle 로거가 그대로 처리한다. */
public final class GradleToolLog implements ToolLog {
    private final Logger logger;

    private GradleToolLog(Logger logger) {
        this.logger = logger;
    }

    public static ToolLog of(Logger logger) {
        return new GradleToolLog(logger);
    }

    @Override
    public void lifecycle(String format, Object... args) {
        logger.lifecycle(format, args);
    }

    @Override
    public void info(String format, Object... args) {
        logger.info(format, args);
    }

    @Override
    public void warn(String format, Object... args) {
        logger.warn(format, args);
    }

    @Override
    public void debug(String format, Object... args) {
        logger.debug(format, args);
    }
}
