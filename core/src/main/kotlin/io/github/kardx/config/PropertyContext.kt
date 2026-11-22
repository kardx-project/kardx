package io.github.kardx.config

import kotlin.reflect.KProperty1

/**
 * 설명을 결정할 때 [DescriptionResolver] 에 넘어가는 프로퍼티 정보. 라이브러리가 만들어 넘기므로 직접 만들 수 없고,
 * 앞으로 정보가 더해질 수 있다.
 *
 * [annotations] 는 Kotlin 프로퍼티, 백킹 필드(`@field:`), getter(`@get:`), 주 생성자 파라미터,
 * Java record component / getter 에 붙은 어노테이션을 모두 합친 목록이다.
 * 그래서 어노테이션의 use-site target 과 상관없이 [findAnnotation] 으로 찾을 수 있다.
 *
 * @property name JSON 필드 이름 (`@JsonProperty` 반영)
 * @property annotations 이 프로퍼티에 붙은 어노테이션 전체 (위치별로 합친 목록)
 * @property property Kotlin 프로퍼티. Java record / bean 이면 null
 * @property declaredName 소스에 선언한 이름 (Jackson 이름 규칙 적용 전, `createdAt`)
 * @property declaringClass 프로퍼티를 선언한 클래스. 모르면 null
 */
public class PropertyContext internal constructor(
    public val name: String,
    public val annotations: List<Annotation>,
    public val property: KProperty1<*, *>?,
    public val declaredName: String,
    public val declaringClass: Class<*>?,
) {
    /** [annotations] 에서 [T] 타입 어노테이션을 처음 것 하나 찾는다. 없으면 null. `it.findAnnotation<Schema>()?.description` */
    public inline fun <reified T : Annotation> findAnnotation(): T? = annotations.firstOrNull { it is T } as T?
}
