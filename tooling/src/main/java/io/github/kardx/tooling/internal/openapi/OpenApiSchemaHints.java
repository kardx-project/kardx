package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.i18n.DocsLanguage;
import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import java.util.List;
import java.util.Map;

/**
 * 라이브러리가 디스크립터 속성으로 남긴 스키마 정보를 epages 가 만든 스펙에 합친다. epages 는 이 속성을 읽지 않는다.
 * <ul>
 *   <li>{@code schema}: 타입·Bean Validation 에서 만든 조각 ({@code integer}+{@code int64}, {@code format: date-time},
 *       {@code minLength}, {@code maximum}, 값 배열의 {@code items} …). 스키마의 {@code type} 과 맞지 않으면 버린다
 *       ({@code number} 는 {@code integer} 로 좁힐 수 있다). {@code items} 는 원소가 값일 때만 합친다</li>
 *   <li>{@code default}·{@code example}: {@code withDefaultValue}·{@code example} 수식어 값. 스키마 타입에 맞춰 숫자·불리언으로 바꾼다</li>
 *   <li>{@code constraintNote}: 키워드로 옮기지 못한 제약. 설명 뒤에 {@code 제약: ...} 으로 붙인다</li>
 *   <li>{@code requiredByConstraint}: {@code @NotNull} 등이 붙은 nullable 항목, {@code requiredInParent}: 부모가 optional 이라
 *       optional 이지만 부모가 있으면 꼭 있는 하위 필드. 부모 스키마의 {@code required} 에 넣고 {@code nullable} 을 뺀다</li>
 *   <li>{@code nullable}: optional 필드의 값이 null 일 수 있는지. epages 는 값 필드에만, 그것도 optional 이면 모두
 *       {@code nullable: true} 를 붙이므로 이 값으로 맞춘다 (기본값이 있는 non-null 필드는 빼고, nullable 객체·배열에는 붙인다).
 *       {@code nullableItems}: 배열 원소가 null 일 수 있으면({@code List<Box?>}) {@code items} 에 {@code nullable: true}</li>
 *   <li>{@code deprecated}: {@code @Deprecated} 프로퍼티나 {@code deprecated()} 수식어. 파라미터와 필드 스키마에
 *       {@code deprecated: true}</li>
 *   <li>{@code masked}·{@code maskingRule}: {@code @Masked} 등 마스킹 어노테이션이나 {@code masked()} 수식어. 필드 스키마의
 *       설명 끝에 {@code Masked: 규칙}, {@code x-masked: true}, {@code x-masked-rule}</li>
 * </ul>
 * 대상은 path·query·header 파라미터, 응답 헤더, 폼 파라미터, 요청·응답 본문 필드다. 같은 스키마를 여러 조각이 고치면 조각 경로 이름순으로
 * 앞선 것이 남는다(테스트 실행 순서와 무관).
 */
final class OpenApiSchemaHints {
    private OpenApiSchemaHints() {
    }

    /** @return 정보를 합친 항목 수 */
    static int transform(Map<String, Object> document, List<Resource> resources, DocsLanguage language) {
        SharedSchemaMarks marks = SharedSchemaMarks.collect(document, resources);
        int changed = 0;

        // 뒤에서부터 적용해 같은 스키마를 여러 조각이 고치면 이름순으로 앞선 조각이 마지막에 남는다
        for (int i = resources.size() - 1; i >= 0; i--) {
            Resource resource = resources.get(i);
            Map<String, Object> operation = resource.operation(document);

            if (operation != null) {
                changed += ParameterHints.apply(operation, resource, language);
                changed += BodyFieldHints.apply(document, operation, resource, marks, language);
            }
        }

        return changed;
    }
}
