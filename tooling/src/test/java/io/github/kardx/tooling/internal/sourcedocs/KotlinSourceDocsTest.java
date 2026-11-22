package io.github.kardx.tooling.internal.sourcedocs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Kotlin 소스 스캔 ({@link KotlinDeclarations}, {@link SourceLexer}). */
class KotlinSourceDocsTest {

    @Test
    void propertyDocPrecedenceOwnKdocOverPropertyTagOverParamTag() {
        Map<String, Object> types = types("""
            package com.example.user

            import com.fasterxml.jackson.annotation.JsonProperty
            import java.time.LocalDate as Date

            /**
             * 사용자 응답.
             *
             * 탈퇴한 사용자는 포함하지 않는다.
             * @property nickname 클래스 KDoc 의 닉네임
             * @property email 메일 주소
             * @param email 생성자 파라미터 메일
             * @param age 나이
             */
            data class UserResponse(
                /** 사용자 ID */
                @JsonProperty("id") val id: Long,
                /** 자기 KDoc 의 닉네임 */
                val nickname: String,
                val email: String,
                private val age: Int = listOf(1, 2).sum(),
                val tags: Map<String, List<Int>> = emptyMap<String, List<Int>>(),
            )
            """);

        assertEquals(Map.of(
            "description", "사용자 응답. 탈퇴한 사용자는 포함하지 않는다.",
            "properties", Map.of(
                "id", "사용자 ID", "nickname", "자기 KDoc 의 닉네임", "email", "메일 주소", "age", "나이")),
            types.get("com.example.user.UserResponse"));
    }

    @Test
    void rawStringsTemplatesAndCommentsDoNotProduceDeclarations() {
        Map<String, Object> types = types("""
            package p

            const val SQL = ""\"
                /** not a doc */ class Fake(val x: Int) { }
                ${ "nested } brace" + listOf("/** x */").size }
            ""\"

            /* block /* nested */ class StillComment */
            // line /** class LineFake */

            /** 진짜 */
            class Real(
                /** 문자열 안의 따옴표 */
                val text: String = "a \\" /** b */ ${ if (true) "}" else "{" } c",
                /** 문자 */
                val ch: Char = '"',
            )
            """);

        assertEquals(Map.of("p.Real", Map.of("description", "진짜", "properties",
            Map.of("text", "문자열 안의 따옴표", "ch", "문자"))), types);
    }

    @Test
    void nestedClassesCompanionAndObjectsUseBinaryNames() {
        Map<String, Object> types = types("""
            package p

            /** 바깥 */
            class Outer {
                /** 안쪽 */
                class Inner(/** 값 */ val value: Int) {
                    /** 더 안쪽 */
                    inner class Deep
                }

                /** 동반 객체 */
                companion object {
                    /** 상수 */
                    const val MAX = 10
                }

                /** 이름 있는 object */
                object Named

                val anonymous = object : Runnable { override fun run() {} }

                /** 본문 속성 */
                var count: Int = 0
                    private set
            }

            class Holder {
                /** 이름 붙은 동반 객체 */
                companion object Factory
            }
            """);

        assertEquals(Map.of("description", "바깥", "properties", Map.of("count", "본문 속성")), types.get("p.Outer"));
        assertEquals(Map.of("description", "안쪽", "properties", Map.of("value", "값")), types.get("p.Outer$Inner"));
        assertEquals(Map.of("description", "더 안쪽"), types.get("p.Outer$Inner$Deep"));
        assertEquals(Map.of("description", "동반 객체", "properties", Map.of("MAX", "상수")),
            types.get("p.Outer$Companion"));
        assertEquals(Map.of("description", "이름 있는 object"), types.get("p.Outer$Named"));
        assertEquals(Map.of("description", "이름 붙은 동반 객체"), types.get("p.Holder$Factory"));
    }

    @Test
    void annotationsWithLambdasArraysAndGenericsAreSkipped() {
        Map<String, Object> types = types("""
            package p

            @Target(AnnotationTarget.CLASS)
            @Suppress(names = ["a", "b"])
            @Ann(block = Foo::class, values = [1, 2], nested = Inner(x = { it > 0 }))
            /** 머리 뒤 KDoc */
            @field:Deprecated("x")
            class Generic<T : Comparable<T>, R : Map<String, List<Pair<Int, T>>>>(
                /** 함수 타입 */
                val handler: suspend (Map<String, Int>) -> Unit,
                /** 수신 객체 함수 타입 */
                val builder: String.(Int) -> List<T>? = { emptyList() },
                /**
                 * 여러 줄
                 * 설명.
                 */
                @param:Ann
                @get:JsonProperty("multi")
                val multi: Array<out R>,
            ) : Base<T>(listOf<T>()), Comparable<Generic<T, R>> where T : Any {
                override fun compareTo(other: Generic<T, R>): Int = 0
            }
            """);

        assertEquals(Map.of("description", "머리 뒤 KDoc", "properties", Map.of(
            "handler", "함수 타입", "builder", "수신 객체 함수 타입", "multi", "여러 줄 설명.")), types.get("p.Generic"));
    }

    @Test
    void enumConstantsWithArgumentsAndBodies() {
        Map<String, Object> types = types("""
            package p

            /** 상태 */
            enum class Status(val label: String) {
                /** 활성 사용자 */
                ACTIVE("활성"),

                /** 휴면 */
                @Deprecated("x")
                DORMANT("휴면") {
                    override fun next() = ACTIVE
                },
                WITHDRAWN("탈퇴") { override fun next() = this },
                ;

                /** 다음 상태 */
                open fun next(): Status = this
            }

            enum class Simple { /** 하나 */ ONE, TWO }
            """);

        assertEquals(Map.of("description", "상태", "enumConstants", Map.of("ACTIVE", "활성 사용자", "DORMANT", "휴면")),
            types.get("p.Status"));
        assertEquals(Map.of("enumConstants", Map.of("ONE", "하나")), types.get("p.Simple"));
    }

    @Test
    void methodsUseSummaryDescriptionAndArityOnlyForDifferingOverloads() {
        Map<String, Object> methods = methods("""
            package p

            @RestController
            @RequestMapping("/users")
            class UserController(private val service: UserService) {
                /**
                 * 사용자를 조회한다. 탈퇴한 사용자는 404.
                 *
                 * @param id 사용자 ID
                 * @return 사용자
                 */
                @GetMapping("/{id}")
                fun getUser(@PathVariable id: Long): UserResponse = service.find(id)
                    ?: throw NotFound()

                /** 이름으로 찾는다. */
                @GetMapping(params = ["name"])
                fun search(@RequestParam name: String): List<UserResponse> {
                    val x = object : Comparator<Int> { override fun compare(a: Int, b: Int) = a - b }
                    return listOf()
                }

                /** 이름과 나이로 찾는다. */
                fun search(
                    name: String,
                    age: Int = if (1 < 2) 3 else 4,
                    block: (Int, Int) -> Unit = { a, b -> },
                ): List<UserResponse> = emptyList()

                /** 같은 문서 */
                fun same(a: Int) = a

                /** 같은 문서 */
                fun same(a: Int, b: Int) = a + b

                fun undocumented() {}

                /** 제네릭 확장 */
                fun <T> List<T>.second(): T = this[1]
            }
            """);

        assertEquals(Map.of("summary", "사용자를 조회한다", "description", "사용자를 조회한다. 탈퇴한 사용자는 404.",
            "params", Map.of("id", "사용자 ID")), methods.get("p.UserController#getUser"));
        assertEquals(Map.of("summary", "이름으로 찾는다", "description", "이름으로 찾는다."),
            methods.get("p.UserController#search/1"));
        assertEquals(Map.of("summary", "이름과 나이로 찾는다", "description", "이름과 나이로 찾는다."),
            methods.get("p.UserController#search/3"));
        assertNull(methods.get("p.UserController#search"), "문서가 다른 오버로드는 이름만으로 쓰지 않는다");
        assertEquals(Map.of("summary", "같은 문서", "description", "같은 문서"), methods.get("p.UserController#same"));
        assertFalse(methods.containsKey("p.UserController#undocumented"));
        assertEquals(Map.of("summary", "제네릭 확장", "description", "제네릭 확장"),
            methods.get("p.UserController#second"));
    }

    @Test
    void bodyPropertiesGettersDelegatesAndExpressionBodies() {
        Map<String, Object> types = types("""
            package p

            class Body {
                /** 지연 */
                val lazyValue by lazy { compute() }

                /** 게터 */
                val computed: Int
                    get() = 1

                val String.extension: Int get() = length

                /** 식 본문 뒤 */
                val afterExpression = foo(1)
                    .let { it + 1 }

                fun f() = run { 1 }

                /** 마지막 */
                lateinit var last: String

                private val data = value
                /** 수식어 같은 이름 뒤 */
                val next = 1
            }
            """);

        assertEquals(Map.of("properties", Map.of("lazyValue", "지연", "computed", "게터",
            "afterExpression", "식 본문 뒤", "last", "마지막", "next", "수식어 같은 이름 뒤")), types.get("p.Body"));
    }

    @Test
    void secondaryConstructorParamsAndKdocMarkup() {
        Map<String, Object> types = types("""
            package p

            /**
             * [Order] 의 [항목][OrderItem]. [문서](https://example.com) 참고, `list[0]` 는 그대로.
             * @constructor 만든다
             */
            class Item private constructor(val sku: String) {
                /**
                 * 보조 생성자.
                 * @param sku 상품 코드
                 */
                constructor(code: Int) : this(code.toString())
            }
            """);

        assertEquals(Map.of("description", "Order 의 항목. 문서 참고, `list[0]` 는 그대로.",
            "properties", Map.of("sku", "상품 코드")), types.get("p.Item"));
    }

    @Test
    void unknownSyntaxSkipsOnlyThatDeclaration() {
        SourceDocsIndex index = new SourceDocsIndex(SourceDocsOptions.ALL);

        SourceDocsScanner.scan("""
            package p
            /** 깨진 */
            class Broken(val x: Int = {{{ ,
            """, SourceLanguage.KOTLIN, index);
        SourceDocsScanner.scan("""
            package p
            /** 다음 파일 */
            class Fine
            """, SourceLanguage.KOTLIN, index);

        assertEquals(Map.of("description", "다음 파일"), types(index).get("p.Fine"));
    }

    @Test
    void methodDocsOffKeepsTypesAndLeavesOutMethods() {
        SourceDocsIndex index = new SourceDocsIndex(SourceDocsOptions.withMethodDocs(false));

        SourceDocsScanner.scan("""
            package p
            /** 사용자 API */
            class UserController {
                /** 구현 메모: 캐시를 먼저 본다. */
                fun getUser(id: Long): String = ""
            }
            """, SourceLanguage.KOTLIN, index);

        assertEquals(Map.of("description", "사용자 API"), types(index).get("p.UserController"));
        assertFalse(index.toJson().containsKey("methods"), index.toJson().toString());
    }

    @Test
    void eachSwitchLeavesOutOnlyItsItem() {
        String source = """
            package p

            /** 주문 */
            class Order(/** 주문 ID */ val id: Long)

            enum class Status {
                /** 활성 */
                ACTIVE,
            }

            class OrderController {
                /**
                 * 주문을 조회한다.
                 * @param id 주문 ID
                 */
                fun getOrder(id: Long): Order = load(id)
            }
            """;

        assertEquals(Map.of("p.Order", Map.of("description", "주문"), "p.Status", Map.of("enumConstants",
            Map.of("ACTIVE", "활성"))), types(scan(source, new SourceDocsOptions(false, true, true, true, true))),
            "fieldDocs = false");
        assertEquals(Map.of("p.Order", Map.of("description", "주문", "properties", Map.of("id", "주문 ID"))),
            types(scan(source, new SourceDocsOptions(true, false, true, true, true))), "enumDocs = false");
        assertEquals(Map.of("properties", Map.of("id", "주문 ID")),
            types(scan(source, new SourceDocsOptions(true, true, false, true, true))).get("p.Order"),
            "schemaDescriptions = false");
        assertEquals(Map.of("p.OrderController#getOrder", Map.of("summary", "주문을 조회한다", "description", "주문을 조회한다.")),
            methods(scan(source, new SourceDocsOptions(true, true, true, false, true))), "paramDocs = false");
        assertFalse(scan(source, new SourceDocsOptions(true, true, true, false, false)).toJson().containsKey("methods"),
            "paramDocs·methodDocs 를 모두 끄면 메서드가 빠진다");
        SourceDocsOptions none = new SourceDocsOptions(false, false, false, false, false);

        assertEquals(Map.of("version", 1), scan(source, none).toJson(), "모두 끄면 빈 색인");
    }

    @Test
    void methodParamTagsAreWrittenEvenWithoutMethodTexts() {
        String source = """
            package p

            class MemoController {
                /**
                 * 메모를 조회한다.
                 *
                 * @param id 메모 [ID][MemoId]
                 *   (양수)
                 * @param includeDeleted 삭제된 메모도 포함할지
                 * @param id 두 번째 태그는 무시한다
                 * @param empty
                 * @return 메모
                 */
                fun <T> getMemo(@PathVariable id: Long, @RequestParam includeDeleted: Boolean = false): T = load(id)

                /** 구현 메모만 있다. */
                fun list(): List<Memo> = emptyList()
            }
            """;
        Map<String, String> params = new LinkedHashMap<>();

        params.put("id", "메모 ID (양수)");
        params.put("includeDeleted", "삭제된 메모도 포함할지");

        assertEquals(Map.of("p.MemoController#getMemo", Map.of("params", params)), methods(source, false),
            "methodDocs = false 면 params 만 남고 params 없는 메서드는 빠진다");
        assertEquals(Map.of("summary", "메모를 조회한다", "description", "메모를 조회한다.", "params", params),
            methods(source, true).get("p.MemoController#getMemo"));
        Map<String, String> ordered = params(methods(source, true), "p.MemoController#getMemo");

        assertEquals(List.of("id", "includeDeleted"), List.copyOf(ordered.keySet()), "주석 순서를 지킨다");
    }

    @Test
    void overloadParamsUseArityKeysOnlyWhenTheyDiffer() {
        String source = """
            package p

            class SearchController {
                /**
                 * 이름으로 찾는다.
                 * @param name 이름
                 */
                fun search(name: String) = 1

                /**
                 * 이름과 나이로 찾는다.
                 * @param name 이름
                 * @param age 나이
                 */
                fun search(name: String, age: Int) = 2

                /** @param id 같은 설명 */
                fun same(id: Long) = 1

                /**
                 * 다른 본문이지만 params 는 같다.
                 * @param id 같은 설명
                 */
                fun same(id: Long, x: Int) = 2

                /**
                 * 본문.
                 * @param id ID
                 */
                fun mixed(id: Long) = 1

                fun mixed(id: Long, other: Int) = 2
            }
            """;
        Map<String, Object> off = methods(source, false);

        assertEquals(Map.of("params", Map.of("name", "이름")), off.get("p.SearchController#search/1"));
        assertEquals(Map.of("params", Map.of("name", "이름", "age", "나이")), off.get("p.SearchController#search/2"));
        assertNull(off.get("p.SearchController#search"));
        assertEquals(Map.of("params", Map.of("id", "ID")), off.get("p.SearchController#mixed/1"));
        assertFalse(off.containsKey("p.SearchController#mixed/2"));
        assertFalse(off.containsKey("p.SearchController#mixed"));
        assertEquals(Map.of("params", Map.of("id", "같은 설명")), off.get("p.SearchController#same"),
            "본문을 적지 않으면 params 만 비교한다");

        Map<String, Object> on = methods(source, true);

        assertEquals(Map.of("summary", "이름으로 찾는다", "description", "이름으로 찾는다.", "params", Map.of("name", "이름")),
            on.get("p.SearchController#search/1"));
        assertEquals(Map.of("summary", "본문", "description", "본문.", "params", Map.of("id", "ID")),
            on.get("p.SearchController#mixed/1"));
        assertEquals(Map.of("params", Map.of("id", "같은 설명")), on.get("p.SearchController#same/1"),
            "본문이 없으면 params 만 적는다");
        assertEquals(Map.of("summary", "다른 본문이지만 params 는 같다", "description", "다른 본문이지만 params 는 같다.",
            "params", Map.of("id", "같은 설명")), on.get("p.SearchController#same/2"));
    }

    @Test
    void brokenMethodDoesNotStopParamCollection() {
        SourceDocsIndex index = new SourceDocsIndex(SourceDocsOptions.withMethodDocs(false));

        SourceDocsScanner.scan("""
            package p
            class Broken {
                /** @param x 깨진 */
                fun broken(x: Int = {{{ ,
            """, SourceLanguage.KOTLIN, index);
        SourceDocsScanner.scan("""
            package p
            class Fine {
                /** @param id 아이디 */
                fun get(id: Long) = id
            }
            """, SourceLanguage.KOTLIN, index);

        assertEquals(Map.of("params", Map.of("id", "아이디")), methods(index).get("p.Fine#get"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> params(Map<String, Object> methods, String key) {
        return (Map<String, String>) ((Map<String, Object>) methods.get(key)).get("params");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> methods(SourceDocsIndex index) {
        return (Map<String, Object>) index.toJson().getOrDefault("methods", Map.of());
    }

    private static Map<String, Object> methods(String source, boolean includeMethodTexts) {
        SourceDocsIndex index = new SourceDocsIndex(SourceDocsOptions.withMethodDocs(includeMethodTexts));

        SourceDocsScanner.scan(source, SourceLanguage.KOTLIN, index);

        return methods(index);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> types(SourceDocsIndex index) {
        return (Map<String, Object>) index.toJson().getOrDefault("types", Map.of());
    }

    private static Map<String, Object> types(String source) {
        return types(scan(source));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> methods(String source) {
        return (Map<String, Object>) scan(source).toJson().getOrDefault("methods", Map.of());
    }

    private static SourceDocsIndex scan(String source) {
        return scan(source, SourceDocsOptions.ALL);
    }

    private static SourceDocsIndex scan(String source, SourceDocsOptions options) {
        SourceDocsIndex index = new SourceDocsIndex(options);

        SourceDocsScanner.scan(source, SourceLanguage.KOTLIN, index);

        return index;
    }
}
