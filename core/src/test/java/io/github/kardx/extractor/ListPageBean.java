package io.github.kardx.extractor;

import java.util.List;

/**
 * 상위 클래스와 같은 이름의 타입 파라미터(T)를 다른 타입으로 넘기는 Java bean.
 * 상속한 {@code getData()} 의 T 는 상위 클래스의 T 이므로 {@code List<T>} 로 풀려야 한다.
 */
public class ListPageBean<T> extends GenericBean<List<T>> {
    private int total;

    public int getTotal() {
        return total;
    }
}
