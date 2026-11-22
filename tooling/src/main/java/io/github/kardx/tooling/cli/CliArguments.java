package io.github.kardx.tooling.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code <명령> --이름 값 --스위치 …} 꼴의 인자. 값을 받는 옵션은 여러 번 줄 수 있다 ({@code --server a --server b}).
 * 잘못된 인자는 {@link UsageException} 이다.
 */
final class CliArguments {
    private final String command;
    private final Map<String, List<String>> options;
    private final Set<String> flags;

    private CliArguments(String command, Map<String, List<String>> options, Set<String> flags) {
        this.command = command;
        this.options = options;
        this.flags = flags;
    }

    /**
     * @param valued 값을 받는 옵션 이름 ({@code --} 없이)
     * @param switches 값 없는 스위치 이름
     */
    public static CliArguments parse(List<String> args, Set<String> valued, Set<String> switches) {
        if (args.isEmpty() || args.get(0).startsWith("--")) {
            throw new UsageException("Missing command");
        }

        Map<String, List<String>> options = new LinkedHashMap<>();
        Set<String> flags = new LinkedHashSet<>();

        for (int index = 1; index < args.size(); index++) {
            String arg = args.get(index);
            String name = arg.startsWith("--") ? arg.substring(2) : null;

            if (name != null && switches.contains(name)) {
                flags.add(name);
            } else if (name != null && valued.contains(name)) {
                if (index + 1 >= args.size()) {
                    throw new UsageException(arg + " needs a value");
                }

                options.computeIfAbsent(name, key -> new ArrayList<>()).add(args.get(++index));
            } else {
                throw new UsageException("Unknown argument '" + arg + "'");
            }
        }

        return new CliArguments(args.get(0), options, flags);
    }

    public String command() {
        return command;
    }

    /** 마지막으로 준 값. 없으면 null. */
    public String value(String name) {
        List<String> values = options.get(name);

        return values == null ? null : values.get(values.size() - 1);
    }

    public List<String> values(String name) {
        return options.getOrDefault(name, List.of());
    }

    public boolean flag(String name) {
        return flags.contains(name);
    }

    /** 명령줄 사용법이 틀렸다 (종료 코드 2). */
    static final class UsageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }
}
