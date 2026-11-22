package io.github.kardx.tooling.internal.openapi;

import io.github.kardx.tooling.internal.openapi.OpenApiResources.Resource;
import java.util.Comparator;
import java.util.List;

/**
 * 같은 operation 으로 합쳐진 조각 중 대표를 고르는 순서. epages 버전·테스트 실행 순서와 상관없이 같은 조각을 고르도록
 * 성공 응답(200, 201, 202, 204, 그 밖의 2xx)을 먼저, 나머지를 뒤에 두고 같은 순위는 조각 id 순으로 둔다.
 * HTTP Client 파일({@code internal.httpclient})도 같은 순위로 operation 의 대표 요청을 고른다.
 */
public final class PrimaryResources {
    private static final int SUCCESS_MIN = 200;
    private static final int SUCCESS_MAX = 299;
    private static final List<Integer> PRIMARY_STATUSES = List.of(200, 201, 202, 204);

    /** 대표 조각이 먼저 오는 순서. */
    static final Comparator<Resource> PRIMARY_FIRST =
        Comparator.comparingInt((Resource r) -> statusPriority(r.status())).thenComparing(Resource::id);

    private PrimaryResources() {
    }

    /** 성공 응답(2xx)을 만든 조각인지. null 이면 false. */
    static boolean isSuccess(Resource resource) {
        return resource != null && resource.status() >= SUCCESS_MIN && resource.status() <= SUCCESS_MAX;
    }

    /** 대표 조각을 고르는 응답 상태 순위. 작을수록 앞선다 (200, 201, 202, 204, 그 밖의 2xx, 나머지). */
    public static int statusPriority(int status) {
        int index = PRIMARY_STATUSES.indexOf(status);

        if (index >= 0) {
            return index;
        }

        return status >= SUCCESS_MIN && status <= SUCCESS_MAX ? PRIMARY_STATUSES.size() : PRIMARY_STATUSES.size() + 1;
    }
}
