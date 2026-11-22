package io.github.kardx.extractor;

import io.github.kardx.annotation.Description;

/** 필드·getter·setter 중 한 곳에만 설명을 붙인 Java bean. */
public class DescribedJavaBean {
    @Description("필드에 붙인 설명")
    private String onField;
    private String onGetter;
    private String onSetter;

    public String getOnField() { return onField; }

    @Description("getter 에 붙인 설명")
    public String getOnGetter() { return onGetter; }

    public String getOnSetter() { return onSetter; }

    @Description("setter 에 붙인 설명")
    public void setOnSetter(String onSetter) { this.onSetter = onSetter; }
}
