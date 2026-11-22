package io.github.kardx.tooling.internal.sourcedocs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.kardx.tooling.internal.io.ToolLog;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Java 소스 스캔 ({@link JavaDeclarations}). */
class JavaSourceDocsTest {

    @Test
    void fieldJavadocOverGetterOverRecordParam() {
        Map<String, Object> types = types("""
            package com.example;

            import java.util.*;
            import static java.util.Map.entry;

            /**
             * 쿠폰 응답.
             *
             * @param code 쿠폰 코드
             * @param rate 할인율
             * @param <T> 타입 파라미터
             */
            public record CouponResponse<T>(
                    @ApiField("쿠폰 코드") String code,
                    /** 컴포넌트 자신의 문서 */
                    int rate,
                    Map<String, List<T>> extra) implements Comparable<CouponResponse<T>> {

                /** 접근자 문서 */
                public String code() { return code; }

                public CouponResponse {
                    Objects.requireNonNull(code);
                }
            }
            """);

        assertEquals(Map.of("description", "쿠폰 응답.", "properties", Map.of("code", "접근자 문서", "rate",
            "컴포넌트 자신의 문서")), types.get("com.example.CouponResponse"));
    }

    @Test
    void classFieldsGettersAndNestedTypes() {
        Map<String, Object> types = types("""
            package com.example;

            /**
             * 주문.<p>
             * {@link Order 주문}은 {@code List<Item>} 을 갖는다. {@link #items}, {@linkplain java.util.List 목록},
             * {@value #MAX}, {@inheritDoc}&lt;끝&gt; &amp; <b>굵게</b>
             * <ul><li>하나</li></ul>
             */
            @Entity
            @Table(name = "orders", indexes = {@Index(columnList = "a, b")})
            public class Order extends Base<Order> implements java.io.Serializable {
                /** 최대 */
                public static final int MAX = 10, MIN = 1;

                /** 필드 문서 */
                private String status;

                private Map<String, Integer> counts = new HashMap<String, Integer>(), other;

                /** getter 문서 (필드 문서가 이긴다) */
                public String getStatus() { return status; }

                /** 사용자 이름 */
                public String getUserName() { return ""; }

                /** URL */
                public String getURL() { return ""; }

                /** 활성 여부 */
                public boolean isActive() { return true; }

                /** 파라미터가 있으면 getter 가 아니다 */
                public String getValue(int index) { return ""; }

                static {
                    String s = "/** not a doc */ class X {";
                }

                /** 중첩 */
                public static class Item {
                    /** 수량 */
                    int quantity;

                    /** 안쪽의 안쪽 */
                    interface Deep {}
                }

                /** 내부 enum */
                enum Kind {
                    /** 일반 */
                    NORMAL,
                    /** 특별 */
                    SPECIAL { @Override public String toString() { return "s"; } };

                    /** 상수 뒤 메서드 */
                    public String label() { return name(); }
                }

                /** 애노테이션 타입 */
                @interface Marker {
                    String value() default "";
                    int[] numbers() default {1, 2};
                }

                public non-sealed class Open {}
            }
            """);

        assertEquals(Map.of(
            "description", "주문. 주문은 `List<Item>` 을 갖는다. items, 목록, MAX, <끝> & 굵게 하나",
            "properties", Map.of("MAX", "최대", "MIN", "최대", "status", "필드 문서", "userName", "사용자 이름",
                "URL", "URL", "active", "활성 여부")),
            types.get("com.example.Order"));
        assertEquals(Map.of("description", "중첩", "properties", Map.of("quantity", "수량")),
            types.get("com.example.Order$Item"));
        assertEquals(Map.of("description", "안쪽의 안쪽"), types.get("com.example.Order$Item$Deep"));
        assertEquals(Map.of("description", "내부 enum", "enumConstants", Map.of("NORMAL", "일반", "SPECIAL", "특별")),
            types.get("com.example.Order$Kind"));
        assertEquals(Map.of("description", "애노테이션 타입"), types.get("com.example.Order$Marker"));
        assertFalse(types.containsKey("com.example.X"));
    }

    @Test
    void controllerMethodsAndOverloads() {
        Map<String, Object> methods = methods("""
            package com.example;

            @RestController
            public class UserController {
                private final UserService service;

                public UserController(UserService service) { this.service = service; }

                /**
                 * 사용자를 조회한다.
                 * <p>
                 * 탈퇴한 사용자는 404.
                 *
                 * @param id 사용자 ID
                 * @throws NotFoundException 없을 때
                 */
                @GetMapping(value = "/{id}", produces = {"application/json"})
                public ResponseEntity<Map<String, List<UserResponse>>> getUser(
                        @PathVariable("id") Long id) throws NotFoundException {
                    return null;
                }

                /** 하나로 찾는다. */
                public <T extends Comparable<T>> List<T> find(@RequestParam(required = false) T a) { return null; }

                /** 둘로 찾는다. */
                public List<String> find(Map<String, Integer> a, String... rest) { return null; }

                public void find() { }
            }
            """);

        assertEquals(Map.of("summary", "사용자를 조회한다", "description", "사용자를 조회한다. 탈퇴한 사용자는 404.",
            "params", Map.of("id", "사용자 ID")), methods.get("com.example.UserController#getUser"));
        assertEquals(Map.of("summary", "하나로 찾는다", "description", "하나로 찾는다."),
            methods.get("com.example.UserController#find/1"));
        assertEquals(Map.of("summary", "둘로 찾는다", "description", "둘로 찾는다."),
            methods.get("com.example.UserController#find/2"));
        assertFalse(methods.containsKey("com.example.UserController#find/0"));
        assertFalse(methods.containsKey("com.example.UserController#find"));
    }

    @Test
    void textBlocksAndCharLiteralsDoNotConfuseTheScanner() {
        Map<String, Object> types = types("""
            package p;

            /** 텍스트 블록 */
            class Texts {
                static final String SQL = \"""
                    /** fake */ class Fake { \\\""" }
                    \""";
                static final char QUOTE = '"';
                static final char ESCAPED = '\\'';
                static final char UNICODE = '\\u0041';

                /** 마지막 */
                String last;
            }
            """);

        assertEquals(Map.of("description", "텍스트 블록", "properties", Map.of("last", "마지막")), types.get("p.Texts"));
        assertFalse(types.containsKey("p.Fake"));
    }

    @Test
    void javadocParamTagsSkipTypeParametersAndSurviveMethodDocsOff() {
        String source = """
            package com.example;

            public class MemoController {
                /**
                 * 메모를 조회한다.
                 *
                 * @param <T> 응답 타입
                 * @param id 메모 {@link MemoId ID}
                 * @param includeDeleted 삭제된 메모도 <b>포함</b>할지
                 * @return 메모
                 */
                public <T> T getMemo(@PathVariable Long id, @RequestParam boolean includeDeleted) { return null; }

                /**
                 * 하나로 찾는다.
                 * @param q 검색어
                 */
                public List<Memo> find(String q) { return null; }

                /**
                 * 둘로 찾는다.
                 * @param q 검색어
                 * @param page 페이지
                 */
                public List<Memo> find(String q, int page) { return null; }

                /** 구현 메모 */
                public void internal() { }
            }
            """;
        Map<String, Object> off = methods(source, false);

        assertEquals(Map.of(
            "com.example.MemoController#getMemo",
            Map.of("params", Map.of("id", "메모 ID", "includeDeleted", "삭제된 메모도 포함할지")),
            "com.example.MemoController#find/1", Map.of("params", Map.of("q", "검색어")),
            "com.example.MemoController#find/2", Map.of("params", Map.of("q", "검색어", "page", "페이지"))), off);

        Map<String, Object> on = methods(source, true);

        assertEquals(Map.of("summary", "메모를 조회한다", "description", "메모를 조회한다.",
            "params", Map.of("id", "메모 ID", "includeDeleted", "삭제된 메모도 포함할지")),
            on.get("com.example.MemoController#getMemo"));
        assertEquals(Map.of("summary", "구현 메모", "description", "구현 메모"), on.get("com.example.MemoController#internal"));
    }

    /** {@code options.encoding = "MS949"} 같은 모듈의 소스도 버리지 않는다. UTF-8 이 아닌 글자만 대체 글자가 된다. */
    @Test
    void nonUtf8SourcesAreReadWithReplacementCharacters(@TempDir File dir) throws IOException {
        File source = new File(dir, "Order.java");

        Files.write(source.toPath(), """
            package com.example;

            /** Order. 주문 */
            public class Order {
                /** Order number */
                private String number;
            }
            """.getBytes(Charset.forName("MS949")));

        Map<String, Object> types = KotlinSourceDocsTest.types(
            SourceDocsScanner.scan(List.of(source), SourceDocsOptions.ALL, ToolLog.NONE));
        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) types.get("com.example.Order");

        assertEquals(Map.of("number", "Order number"), order.get("properties"), String.valueOf(types));
        assertFalse(String.valueOf(order.get("description")).contains("주문"), "MS949 바이트는 UTF-8 글자가 아니다");
    }

    private static Map<String, Object> methods(String source, boolean includeMethodTexts) {
        SourceDocsIndex index = new SourceDocsIndex(SourceDocsOptions.withMethodDocs(includeMethodTexts));

        SourceDocsScanner.scan(source, SourceLanguage.JAVA, index);

        return KotlinSourceDocsTest.methods(index);
    }

    private static Map<String, Object> types(String source) {
        return KotlinSourceDocsTest.types(scan(source));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> methods(String source) {
        return (Map<String, Object>) scan(source).toJson().getOrDefault("methods", Map.of());
    }

    private static SourceDocsIndex scan(String source) {
        SourceDocsIndex index = new SourceDocsIndex(SourceDocsOptions.ALL);

        SourceDocsScanner.scan(source, SourceLanguage.JAVA, index);

        return index;
    }
}
