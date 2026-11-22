package io.github.kardx.extractor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 테스트용 null 아님 표시. 라이브러리는 패키지와 상관없이 단순 이름으로 본다. */
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE_USE,
    ElementType.RECORD_COMPONENT,
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER,
})
public @interface NonNull {
}
