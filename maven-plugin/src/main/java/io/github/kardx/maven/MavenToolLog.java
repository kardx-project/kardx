package io.github.kardx.maven;

import io.github.kardx.tooling.internal.io.ToolLog;
import org.apache.maven.plugin.logging.Log;

/** tooling 로그를 Maven 로그로 보낸다. Gradle 의 lifecycle(기본으로 보임)은 info, info(숨김)는 debug 가 된다. */
final class MavenToolLog implements ToolLog {
    private final Log log;

    MavenToolLog(Log log) {
        this.log = log;
    }

    @Override
    public void lifecycle(String format, Object... args) {
        log.info(ToolLog.format(format, args));
    }

    @Override
    public void info(String format, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(ToolLog.format(format, args));
        }
    }

    @Override
    public void warn(String format, Object... args) {
        log.warn(ToolLog.format(format, args));
    }

    @Override
    public void debug(String format, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(ToolLog.format(format, args));
        }
    }
}
