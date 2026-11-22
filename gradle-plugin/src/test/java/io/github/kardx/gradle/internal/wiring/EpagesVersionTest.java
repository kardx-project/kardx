package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EpagesVersionTest {

    @Test
    void readsVersionFromPluginJarName() {
        assertEquals("0.20.2", EpagesVersion.parse("restdocs-api-spec-gradle-plugin-0.20.2.jar"));
        assertEquals("0.19.4", EpagesVersion.parse("restdocs-api-spec-gradle-plugin-0.19.4.jar"));
        assertNull(EpagesVersion.parse("main"), "클래스 디렉터리(같은 빌드에 포함)면 모른다");
        assertNull(EpagesVersion.parse("restdocs-api-spec-gradle-plugin.jar"));
    }

    @Test
    void pluginLoadedFromClassesDirectoryHasNoVersion() {
        assertNull(EpagesVersion.of(new Object() { }));
    }

    @Test
    void warnsWhenBootAndEpagesLinesDiffer() {
        assertTrue(EpagesVersion.mismatch(4, "0.19.4").contains("Use epages 0.20.x with Spring Boot 4"));
        assertTrue(EpagesVersion.mismatch(3, "0.20.2").contains("Use epages 0.19.x with Spring Boot 3"));
        assertNull(EpagesVersion.mismatch(4, "0.20.2"));
        assertNull(EpagesVersion.mismatch(3, "0.19.4"));
        assertNull(EpagesVersion.mismatch(0, "0.19.4"), "Boot 버전을 모르면 판정하지 않는다");
        assertNull(EpagesVersion.mismatch(4, null));
    }

    /** epages 를 적용하지 않은 apiDocs 는 무엇이 꺼졌고 어떻게 켜는지 한 줄로 알린다. 버전은 Boot 주 버전을 따른다. */
    @Test
    void openApiOffHintNamesThePluginLineForTheBootVersion() {
        assertEquals("off (apply id(\"com.epages.restdocs-api-spec\") version \"0.20.2\" to enable OpenAPI, "
            + "coverage and the viewer)", EpagesVersion.offHint(4));
        assertTrue(EpagesVersion.offHint(3).contains("version \"0.19.4\""));
        assertTrue(EpagesVersion.offHint(0).contains("version \"0.20.2\""), "Boot 버전을 모르면 최신 계열");
    }
}
