package io.github.kardx.extractor;

import java.util.List;

/** primitive 컴포넌트를 가진 Java record. kotlin-reflect 로는 다룰 수 없다. */
public record JavaPoint(int x, long y, String label, List<String> tags) {
}
