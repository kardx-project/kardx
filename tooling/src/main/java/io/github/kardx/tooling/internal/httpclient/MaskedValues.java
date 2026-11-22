package io.github.kardx.tooling.internal.httpclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서용으로 가린 값({@value #MARKER}, 라이브러리 {@code maskedHeaderValue} 의 기본값)을 HTTP Client 변수로 바꾼다. 그대로 두면
 * 파일의 요청이 {@code ****} 를 보내 인증에 실패하기 때문이다. 바꾸는 자리:
 * <ul>
 *   <li>쿼리 파라미터와 {@code application/x-www-form-urlencoded} 본문의 {@code name=****} → {@code name={{name}}}</li>
 *   <li>{@code Cookie} 헤더의 {@code name=****} → {@code name={{name}}}</li>
 *   <li>값 전체가 {@code ****} 인 헤더, 방식만 남긴 인증 헤더({@code Basic ****}) → {@code {{헤더 이름 소문자}}}</li>
 * </ul>
 * 값이 정확히 표시와 같을 때만 바꾸고 다른 값은 그대로 둔다. 변수 이름은 글자·숫자·{@code _} 만 남긴다.
 */
final class MaskedValues {
    static final String MARKER = "****";
    private static final String ENCODED_MARKER = "%2a%2a%2a%2a";
    private static final String FORM = "application/x-www-form-urlencoded";
    private static final String CONTENT_TYPE = "content-type";
    private static final String COOKIE = "cookie";
    private static final Set<String> RESERVED = Set.of(HttpClientFile.BASE_URL, HttpClientFile.TOKEN);
    private static final Pattern NOT_VARIABLE = Pattern.compile("[^A-Za-z0-9_]");
    private static final Pattern SCHEME_ONLY = Pattern.compile("^([A-Za-z][A-Za-z0-9-]*)\\s+\\Q" + MARKER + "\\E$");

    private MaskedValues() {
    }

    /**
     * 가린 값을 바꾼 요청과 새로 쓴 변수들.
     *
     * @param request 변수로 바꾼 요청
     * @param variables 요청에 쓴 변수 이름 (이름순)
     */
    record Replaced(RecordedRequest request, SortedSet<String> variables) {
    }

    static Replaced replace(RecordedRequest request) {
        SortedSet<String> variables = new TreeSet<>();
        String target = request.target();
        int query = target.indexOf('?');

        if (query >= 0) {
            target = target.substring(0, query + 1) + pairs(target.substring(query + 1), '&', variables);
        }

        List<String> headers = new ArrayList<>();
        boolean form = false;

        for (String header : request.headers()) {
            int colon = header.indexOf(':');

            if (colon <= 0) {
                headers.add(header);
                continue;
            }

            String name = header.substring(0, colon).trim();
            String value = header.substring(colon + 1).trim();
            String lower = name.toLowerCase(Locale.ROOT);
            String replaced = header(lower, value, variables);

            form |= lower.equals(CONTENT_TYPE) && value.toLowerCase(Locale.ROOT).startsWith(FORM);
            headers.add(replaced.equals(value) ? header : name + ": " + replaced);
        }

        String body = form ? pairs(request.body(), '&', variables) : request.body();

        return new Replaced(new RecordedRequest(request.method(), target, headers, body), variables);
    }

    private static String header(String lowerName, String value, SortedSet<String> variables) {
        if (lowerName.equals(COOKIE)) {
            return pairs(value, ';', variables);
        }

        if (value.equals(MARKER)) {
            return reference(lowerName, variables);
        }

        Matcher scheme = SCHEME_ONLY.matcher(value);

        return scheme.matches() ? scheme.group(1) + " " + reference(lowerName, variables) : value;
    }

    /** {@code a=1&b=****} 처럼 나뉜 이름=값 쌍에서 가린 값만 변수로 바꾼다. 나머지 글자는 그대로 둔다. */
    private static String pairs(String text, char separator, SortedSet<String> variables) {
        List<String> parts = new ArrayList<>();

        for (String part : text.split(Pattern.quote(String.valueOf(separator)), -1)) {
            int equals = part.indexOf('=');
            String value = equals < 0 ? "" : part.substring(equals + 1).strip();

            if (equals >= 0 && (value.equals(MARKER) || value.toLowerCase(Locale.ROOT).equals(ENCODED_MARKER))) {
                parts.add(part.substring(0, equals + 1) + reference(part.substring(0, equals).strip(), variables));
            } else {
                parts.add(part);
            }
        }

        return String.join(String.valueOf(separator), parts);
    }

    private static String reference(String name, SortedSet<String> variables) {
        String variable = NOT_VARIABLE.matcher(name).replaceAll("_");

        if (variable.isEmpty()) {
            variable = "masked";
        } else if (Character.isDigit(variable.charAt(0))) {
            variable = "_" + variable;
        } else if (RESERVED.contains(variable)) {
            variable = variable + "_value";
        }

        variables.add(variable);

        return "{{" + variable + "}}";
    }
}
