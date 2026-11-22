package io.github.kardx.extractor;

/** 제네릭 getter 를 가진 Java bean. 하위 클래스가 재정의하면 bridge 메서드가 생긴다. */
public class GenericBean<T> {
    private T data;

    public T getData() {
        return data;
    }
}
