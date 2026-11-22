package io.github.kardx.extractor;

/** getter 로만 노출되는 Java bean. */
public class JavaBean {
    private String name;
    private boolean active;
    private int count;

    public String getName() { return name; }
    public boolean isActive() { return active; }
    public int getCount() { return count; }
}
