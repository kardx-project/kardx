package io.github.kardx.extractor;

/** Java {@code @Deprecated} getter 를 추출하는지 확인하는 bean. */
public class JavaDeprecatedBean {
    private final String current = "a";
    private final String legacy = "b";

    public String getCurrent() {
        return current;
    }

    /** @deprecated {@link #getCurrent()} 를 쓴다 */
    @Deprecated
    public String getLegacy() {
        return legacy;
    }
}
