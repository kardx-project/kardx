package io.github.kardx.tooling.internal.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 로그·오류 메시지의 헤더 값 가리기({@link SecretMask}). */
class SecretMaskTest {

    @Test
    void masksWholeHeaderValuesAndLongTokenParts() {
        SecretMask mask = new SecretMask(List.of("Bearer abcdefgh12345", "key-1", "ab"));

        assertEquals("got **** and token **** and key ****, ab stays, Bearer stays",
            mask.apply("got Bearer abcdefgh12345 and token abcdefgh12345 and key key-1, ab stays, Bearer stays"));
    }
}
