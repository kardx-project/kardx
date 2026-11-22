package io.github.kardx.gradle.internal.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DocsLocationsTest {

    @Test
    void stripsEverySpringBootStaticLocation() {
        assertEquals("docs", DocsLocations.webPath("static/docs"));
        assertEquals("docs", DocsLocations.webPath("public/docs"));
        assertEquals("docs", DocsLocations.webPath("resources/docs"));
        assertEquals("api/docs", DocsLocations.webPath("META-INF/resources/api/docs"));
    }

    @Test
    void keepsPathOutsideStaticLocations() {
        assertEquals("docs", DocsLocations.webPath("docs"));
        assertEquals("staticdocs", DocsLocations.webPath("staticdocs"));
    }
}
