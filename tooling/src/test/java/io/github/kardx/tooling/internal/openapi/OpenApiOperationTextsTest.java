package io.github.kardx.tooling.internal.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import io.github.kardx.tooling.internal.spec.SpecMaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 테스트가 정한 operation summary·description ({@link OpenApiOperationTexts}). */
class OpenApiOperationTextsTest {
    private static final String SPEC = """
        openapi: 3.0.1
        info: {title: shop, version: 1}
        paths:
          /payments:
            post:
              summary: 계좌이체 결제
              description: 계좌이체로 결제한다.
              responses: {"201": {description: Created}}
          /refunds:
            post:
              summary: 환불
              description: 환불
              responses: {"200": {description: OK}}
        """;

    @Test
    void overridesMergedOperationWithFirstDeclaredValueAndWarnsOnConflict() {
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        List<String> warnings = new ArrayList<>();
        List<Resource> resources = List.of(
            resource("payment/pay-by-card", "/payments", Map.of("summary", "결제 요청", "description", "주문을 결제한다.")),
            resource("payment/pay-by-bank-transfer", "/payments", Map.of("summary", "결제")));

        OpenApiOperationTexts.transform(document, resources, warnings);

        Map<String, Object> operation = SpecMaps.operation(document, "/payments", "post");

        assertEquals("결제", operation.get("summary"), "스니펫 디렉터리 이름순으로 처음 값 (pay-by-bank-transfer)");
        assertEquals("주문을 결제한다.", operation.get("description"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).startsWith("POST /payments: tests declare different summary values"),
            warnings.get(0));
    }

    @Test
    void summaryAloneReplacesDescriptionOnlyWhenItWasTheSummary() {
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        List<String> warnings = new ArrayList<>();

        OpenApiOperationTexts.transform(document, List.of(
            resource("payment/pay", "/payments", Map.of("summary", "결제 요청")),
            resource("refund/refund", "/refunds", Map.of("summary", "환불 요청"))), warnings);

        Map<String, Object> payment = SpecMaps.operation(document, "/payments", "post");
        Map<String, Object> refund = SpecMaps.operation(document, "/refunds", "post");

        assertEquals("결제 요청", payment.get("summary"));
        assertEquals("계좌이체로 결제한다.", payment.get("description"), "테스트 설명은 그대로");
        assertEquals("환불 요청", refund.get("summary"));
        assertEquals("환불 요청", refund.get("description"), "summary 를 대신 쓰던 description 은 함께 바뀐다");
        assertEquals(List.of(), warnings);
    }

    @Test
    void sourceDocsFillWhatTestsDidNotDeclareWithoutWarnings() {
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);
        List<String> warnings = new ArrayList<>();
        Map<String, Object> source = Map.of("sourceSummary", "결제한다", "sourceDescription", "결제한다. 카드만 된다.");

        OpenApiOperationTexts.transform(document, List.of(
            resource("payment/a", "/payments", source),
            resource("payment/b", "/payments", Map.of("sourceSummary", "다른 값", "sourceDescription", "다른 값.")),
            resource("refund/refund", "/refunds", Map.of("summary", "환불 요청", "sourceSummary", "환불한다",
                "sourceDescription", "환불한다. 부분 환불 가능."))), warnings);

        Map<String, Object> payment = SpecMaps.operation(document, "/payments", "post");
        Map<String, Object> refund = SpecMaps.operation(document, "/refunds", "post");

        assertEquals("결제한다", payment.get("summary"), "소스 문서가 기본값보다 먼저다");
        assertEquals("결제한다. 카드만 된다.", payment.get("description"));
        assertEquals("환불 요청", refund.get("summary"), "테스트가 정한 값이 소스 문서보다 먼저다");
        assertEquals("환불한다. 부분 환불 가능.", refund.get("description"), "정하지 않은 description 은 소스 문서");
        assertEquals(List.of(), warnings, "소스 문서 값끼리, 정한 값과 소스 문서 값 사이에는 경고하지 않는다");
    }

    @Test
    void explicitDescriptionBeatsSourceDescription() {
        Map<String, Object> document = SnippetFixtures.yaml(SPEC);

        OpenApiOperationTexts.transform(document, List.of(resource("refund/refund", "/refunds",
            Map.of("description", "직접 쓴 설명", "sourceSummary", "환불한다", "sourceDescription", "환불한다."))),
            new ArrayList<>());

        Map<String, Object> refund = SpecMaps.operation(document, "/refunds", "post");

        assertEquals("환불한다", refund.get("summary"));
        assertEquals("직접 쓴 설명", refund.get("description"));
    }

    private static Resource resource(String id, String path, Map<String, Object> extras) {
        Map<String, Object> json = SnippetFixtures.yaml("""
            operationId: %s
            request: {path: %s, method: POST}
            response: {status: 201}
            """.formatted(id, path));

        return new Resource(id, json, extras);
    }
}
