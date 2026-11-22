package io.github.kardx.tooling.internal.spec;

import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

/**
 * epages(swagger-core) 출력과 비슷한 모양으로 읽고 쓰는 YAML. 날짜·yes/no 처럼 보이는 문자열을 바꾸지 않아서
 * 읽은 문서를 고쳐 다시 써도 건드리지 않은 값은 그대로 남는다.
 */
public final class SwaggerYaml {
    /** 큰 스펙도 읽도록 SnakeYAML 기본 한도(3MB)를 올린다. */
    private static final int CODE_POINT_LIMIT = 64 * 1024 * 1024;

    private SwaggerYaml() {
    }

    /** YAML 글을 맵·리스트 트리로 읽는다. */
    public static Object load(String text) {
        return yaml().load(text);
    }

    /** 맵·리스트 트리를 YAML 글로 쓴다. */
    public static String dump(Object document) {
        return yaml().dump(document);
    }

    /** {@link Yaml} 은 스레드 안전하지 않으므로 호출마다 만든다. */
    private static Yaml yaml() {
        LoaderOptions loader = new LoaderOptions();
        loader.setCodePointLimit(CODE_POINT_LIMIT);

        DumperOptions dumper = new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setIndent(2);
        dumper.setPrettyFlow(false);
        dumper.setSplitLines(false);
        dumper.setWidth(Integer.MAX_VALUE);
        dumper.setAllowUnicode(true);

        return new Yaml(new SafeConstructor(loader), new SwaggerStyleRepresenter(dumper), dumper, loader,
            new StringPreservingResolver());
    }

    /**
     * 문자열 스타일을 swagger-core(Jackson YAML) 출력과 비슷하게 고른다: 여러 줄은 {@code |-} 블록, 숫자·불리언·날짜처럼
     * 보이거나 YAML 특수문자로 시작하는 값은 큰따옴표, 나머지는 그대로. 판정을 놓친 값은 SnakeYAML 이 작은따옴표로 쓴다.
     */
    private static final class SwaggerStyleRepresenter extends Representer {
        private static final Pattern LOOKS_TYPED = Pattern.compile(
            "^(?:[-+]?(?:\\d[\\d_]*|\\d*\\.\\d+|\\.inf|\\.nan)(?:[eE][-+]?\\d+)?|0x[0-9a-fA-F]+|0o?[0-7]+"
                + "|\\d{4}-\\d{1,2}-\\d{1,2}.*|true|false|yes|no|on|off|y|n|null|~)$",
            Pattern.CASE_INSENSITIVE);
        private static final Pattern SPECIAL_START = Pattern.compile("^[-?:,\\[\\]{}#&*!|>'\"%@`].*");

        SwaggerStyleRepresenter(DumperOptions options) {
            super(options);
            representers.put(String.class, data -> {
                String text = (String) data;
                DumperOptions.ScalarStyle style;

                if (text.contains("\n")) {
                    style = DumperOptions.ScalarStyle.LITERAL;
                } else if (needsQuotes(text)) {
                    style = DumperOptions.ScalarStyle.DOUBLE_QUOTED;
                } else {
                    style = DumperOptions.ScalarStyle.PLAIN;
                }

                return representScalar(Tag.STR, text, style);
            });
        }

        private static boolean needsQuotes(String text) {
            return text.isEmpty()
                || Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(text.length() - 1))
                || SPECIAL_START.matcher(text).matches()
                || text.contains(": ") || text.contains(" #") || text.endsWith(":")
                || text.chars().anyMatch(c -> c < 0x20 || c == 0x7f)
                || LOOKS_TYPED.matcher(text).matches();
        }
    }

    /**
     * 암묵 타입 중 true/false, 숫자, null 만 해석한다. {@code 2024-01-01} 같은 날짜나 {@code yes/no/on/off} 는
     * 문자열로 둔다. 그래야 다시 쓸 때 원래 값이 유지된다. 숫자는 YAML 1.2 core 모양만 숫자로 읽는다: SnakeYAML 의
     * YAML 1.1 규칙은 {@code 10:30}(60진수), {@code 0b1}, {@code 1_000}, {@code 012}(8진수)도 숫자로 읽는데,
     * swagger-core(Jackson YAML)는 이런 문자열을 따옴표 없이 쓰므로 후처리가 {@code example: 10:30} 을
     * {@code 630} 으로 바꿔 버린다.
     */
    private static final class StringPreservingResolver extends Resolver {
        private static final Pattern TRUE_FALSE = Pattern.compile("^(?:true|True|TRUE|false|False|FALSE)$");
        private static final Pattern CORE_INT = Pattern.compile("^(?:[-+]?(?:0|[1-9][0-9]*)|0x[0-9a-fA-F]+)$");
        /** 소수점이나 지수가 있어야 한다 ({@code 012} 는 문자열). */
        private static final Pattern CORE_FLOAT = Pattern.compile(
            "^(?:[-+]?(?:[0-9]+\\.[0-9]*|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?|[-+]?[0-9]+[eE][-+]?[0-9]+"
                + "|[-+]?\\.(?:inf|Inf|INF)|\\.(?:nan|NaN|NAN))$");

        @Override
        protected void addImplicitResolvers() {
            addImplicitResolver(Tag.BOOL, TRUE_FALSE, "tTfF");
            addImplicitResolver(Tag.INT, CORE_INT, "-+0123456789");
            addImplicitResolver(Tag.FLOAT, CORE_FLOAT, "-+0123456789.");
            addImplicitResolver(Tag.MERGE, MERGE, "<");
            addImplicitResolver(Tag.NULL, NULL, "~nN\0");
            addImplicitResolver(Tag.NULL, EMPTY, null);
        }
    }
}
