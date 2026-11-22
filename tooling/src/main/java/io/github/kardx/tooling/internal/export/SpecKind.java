package io.github.kardx.tooling.internal.export;

/** 내보내는 스펙 종류. {@link #type()} 은 {@code {type}} 자리표시자 값이자 Maven classifier 다. */
public enum SpecKind {
    OPENAPI("openapi"),
    ASYNCAPI("asyncapi");

    private final String type;

    SpecKind(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}
