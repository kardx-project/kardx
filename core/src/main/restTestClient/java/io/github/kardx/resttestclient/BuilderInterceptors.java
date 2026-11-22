package io.github.kardx.resttestclient;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * {@code RestTestClient.Builder<?>} 에 인터셉터를 단다. 빌더의 자기 타입(B extends Builder&lt;B&gt;)을 Kotlin 으로는 star projection 에서
 * 잡을 수 없어 ({@code RestTestClient.bindTo(mockMvc)} 가 {@code Builder<?>} 를 돌려준다) Java 의 와일드카드 캡처로 부른다.
 */
final class BuilderInterceptors {

    private BuilderInterceptors() {
    }

    static void add(RestTestClient.Builder<?> builder, ClientHttpRequestInterceptor interceptor) {
        builder.requestInterceptor(interceptor);
    }
}
