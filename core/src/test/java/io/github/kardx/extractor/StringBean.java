package io.github.kardx.extractor;

/** {@link GenericBean} 을 String 으로 고정. {@code getData()} bridge 메서드와 축약어 getter 픽스처. */
public class StringBean extends GenericBean<String> {
    @Override
    public String getData() {
        return "x";
    }

    public String getURL() {
        return "";
    }
}
