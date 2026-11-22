package io.github.kardx.extractor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 반복 가능한 Java 어노테이션. 두 번 이상 붙이면 {@link RepeatedDocs} 로 묶인다. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
@Repeatable(RepeatedDocs.class)
public @interface RepeatedDoc {
    String value();
}
