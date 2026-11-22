package io.github.kardx.tooling.internal.export;

import io.github.kardx.tooling.ToolingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 파일 이름·URL 템플릿의 자리표시자({@code {name}}, {@code {version}}, {@code {type}}, {@code {ext}})를 채운다.
 * 모르는 자리표시자는 오타일 가능성이 크므로 거부한다.
 */
public final class NameTemplate {
    public static final String NAME = "name";
    public static final String VERSION = "version";
    public static final String TYPE = "type";
    public static final String EXT = "ext";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");
    private static final List<String> KNOWN = List.of(NAME, VERSION, TYPE, EXT);

    private NameTemplate() {
    }

    /** 자리표시자 값. */
    public static Map<String, String> values(String name, String version, SpecKind kind, String extension) {
        return Map.of(NAME, name, VERSION, version, TYPE, kind.type(), EXT, extension);
    }

    /**
     * 파일 이름 템플릿을 채운 상대 경로. 비었거나 절대 경로이거나 {@code ..} 로 디렉터리 밖을 가리키면 거부한다.
     *
     * @param property 설정 이름 (오류 메시지용, {@code kardx.export.openApiFileName})
     */
    public static String fileName(String template, Map<String, String> values, String property) {
        String path = resolve(template, values, property, UnaryOperator.identity()).replace('\\', '/');

        if (path.isBlank() || path.startsWith("/") || path.endsWith("/") || path.matches("^[A-Za-z]:.*")
            || List.of(path.split("/")).contains("..")) {
            throw new ToolingException(property + " must be a relative path inside the export directory, got '"
                + template + "' -> '" + path + "'");
        }

        return path;
    }

    /** URL 템플릿을 채운다. 값은 URL 경로에 넣을 수 있게 인코딩한다. */
    public static String url(String template, Map<String, String> values, String property) {
        return resolve(template, values, property,
            value -> URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static String resolve(
        String template, Map<String, String> values, String property, UnaryOperator<String> encode) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String key = matcher.group(1);

            if (!KNOWN.contains(key)) {
                throw new ToolingException("Unknown placeholder {" + key + "} in " + property + " ('" + template
                    + "'). Use {name}, {version}, {type} or {ext}.");
            }

            matcher.appendReplacement(out, Matcher.quoteReplacement(encode.apply(values.get(key))));
        }

        matcher.appendTail(out);

        return out.toString();
    }
}
