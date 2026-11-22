package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.io.JsonFiles;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 이름이 같은데 구조가 다른 OpenAPI 스키마를 epages 가 스펙을 만들기 전에 정리한다.
 *
 * <p>epages 는 스키마를 이름으로 모으므로 이름이 같으면 마지막 하나만 남고, 다른 구조를 쓰는 응답·요청이 모두 그 스키마를
 * 가리키게 된다. 라이브러리가 OpenAPI 조각(resource.json) 옆에 남긴 {@value #METADATA_FILE}(이름의 출처)를 읽어
 * 테스트 실행 순서와 상관없이 같은 결과가 나오게 정한다.
 * <ul>
 *   <li>타입에서 자동으로 정한 이름이 서로 다른 타입끼리 겹치면, 모든 타입이 서로 달라질 때까지 이름을 한정한다.
 *       바깥 클래스 이름, 그다음 패키지 이름을 뒤에서부터 붙인다 ({@code Item} → {@code OrderItem}, {@code CartItem}).
 *       직접 정한 이름과 겹치면 직접 정한 쪽이 이름을 갖는다</li>
 *   <li>같은 타입의 요청과 응답 모양이 다르면(읽기 전용·쓰기 전용 프로퍼티) 요청 스키마에 {@code Request} 를 붙인다</li>
 *   <li>직접 정한 이름(테스트의 {@code openApi { responseSchema }}, {@code openApiErrorSchema})이나 같은 타입·같은 방향끼리
 *       모양이 다르면, 필드를 합친 스키마 하나로 맞춘다. 모든 조각에 있고 필수인 필드만 필수로 남긴다.
 *       같은 경로의 타입이 다르면 합칠 수 없으므로 경고하고 {@code VARIES} 로 둔다</li>
 * </ul>
 */
public final class OpenApiSchemaNames {
    /** 라이브러리의 OpenApiSchemaMetadata.FILE_NAME 과 같다. */
    public static final String METADATA_FILE = "openapi-schemas.json";
    public static final String RESOURCE_FILE = "resource.json";
    private static final int MAX_ROUNDS = 10;

    private OpenApiSchemaNames() {
    }

    /** 정리 결과. {@link #changes} 는 바꾼 내용, {@link #warnings} 는 합칠 수 없어 남긴 충돌이다. */
    public record Result(List<String> changes, List<String> warnings) {
    }

    public static Result resolve(File snippetsDirectory) {
        List<SchemaUsage> usages = SchemaUsage.read(snippetsDirectory);
        List<String> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<Path> touched = new LinkedHashSet<>();

        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean renamed = false;

            for (Map.Entry<String, List<SchemaUsage>> entry : byName(usages).entrySet()) {
                List<SchemaUsage> group = entry.getValue();

                if (group.stream().map(SchemaUsage::structure).distinct().count() > 1) {
                    renamed |= SchemaRenaming.separate(entry.getKey(), group, changes, touched);
                }
            }

            if (!renamed) {
                break;
            }
        }

        // 이름을 나눈 뒤에도 같은 이름에 모양이 여럿이면 같은 것으로 보고 합친다
        byName(usages).forEach((name, group) -> SchemaMerging.merge(name, group, changes, warnings, touched));

        touched.forEach(file -> JsonFiles.write(file.toFile(), usages.stream()
            .filter(usage -> usage.file().equals(file))
            .findFirst()
            .map(SchemaUsage::resource)
            .orElseThrow()));

        return new Result(counted(changes), warnings);
    }

    /** 스키마 이름별 조각. 이름순이라 실행 순서와 상관없이 같은 결과가 나온다. */
    private static Map<String, List<SchemaUsage>> byName(List<SchemaUsage> usages) {
        Map<String, List<SchemaUsage>> byName = new TreeMap<>();
        usages.forEach(usage -> byName.computeIfAbsent(usage.name(), n -> new ArrayList<>()).add(usage));

        return byName;
    }

    /** 같은 내용은 한 줄로 모으고 여러 곳이면 개수를 붙인다 ({@code Item → OrderItem (...) — 3 places}). */
    private static List<String> counted(List<String> messages) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        messages.forEach(message -> counts.merge(message, 1, Integer::sum));

        return counts.entrySet()
            .stream()
            .map(entry -> entry.getValue() > 1 ? entry.getKey() + " — " + entry.getValue() + " places" : entry.getKey())
            .toList();
    }
}
