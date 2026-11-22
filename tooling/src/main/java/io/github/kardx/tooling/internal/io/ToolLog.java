package io.github.kardx.tooling.internal.io;

/**
 * 빌드 도구에 맞는 로그 출력. Gradle 플러그인은 태스크 로거에, Maven 플러그인은 {@code Log} 에, 명령줄은 표준 출력·오류에 잇는다.
 * 메시지 형식은 SLF4J 와 같다: {@code {}} 자리에 인자를 차례로 넣는다.
 */
public interface ToolLog {
    /** 아무것도 출력하지 않는다. */
    ToolLog NONE = new ToolLog() {
        @Override
        public void lifecycle(String format, Object... args) {
            // 버린다
        }

        @Override
        public void info(String format, Object... args) {
            // 버린다
        }

        @Override
        public void warn(String format, Object... args) {
            // 버린다
        }

        @Override
        public void debug(String format, Object... args) {
            // 버린다
        }
    };

    /** 기본으로 보이는 진행 메시지 (Gradle lifecycle 수준). */
    void lifecycle(String format, Object... args);

    void info(String format, Object... args);

    void warn(String format, Object... args);

    void debug(String format, Object... args);

    /** {@code {}} 자리에 인자를 차례로 넣는다. 남는 인자는 버리고, 모자라면 {@code {}} 를 그대로 둔다. */
    static String format(String format, Object... args) {
        StringBuilder out = new StringBuilder();
        int start = 0;
        int index = 0;
        int at = format.indexOf("{}");

        while (at >= 0 && index < args.length) {
            out.append(format, start, at).append(args[index++]);
            start = at + 2;
            at = format.indexOf("{}", start);
        }

        return out.append(format.substring(start)).toString();
    }
}
