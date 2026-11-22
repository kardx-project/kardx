package io.github.kardx.extractor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** public 이 아닌(package-private) Java 어노테이션. 다른 패키지의 리플렉션 호출은 접근 검사에 걸린다. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@interface PackagePrivateDoc {
    String value();
}
