package io.github.kardx.annotation

/**
 * 필드 또는 프로퍼티에 대한 설명을 정의하는 어노테이션.
 *
 * REST Docs 문서 자동 생성 시 해당 필드의 description 으로 사용된다.
 * 다른 어노테이션(예: Swagger 의 `@Schema`)의 설명도 읽으려면 `kardx.description-annotations`
 * (코드로는 `RestDocsSettings.descriptionAnnotations`)에 그 어노테이션을 적는다. `@Description` 이 항상 먼저다.
 * `RestDocsSettings.descriptionResolver` 를 바꾸면 설명을 정하는 방식을 통째로 바꿀 수 있다.
 *
 * ```kotlin
 * data class UserResponse(
 *     @Description("사용자 ID")
 *     val id: Long,
 * )
 * ```
 *
 * 어느 위치에 붙여도 같은 설명으로 읽는다.
 *
 * | 위치 | 예 |
 * |---|---|
 * | Kotlin 생성자 프로퍼티 (use-site target 생략) | `@Description("ID") val id: Long` |
 * | 프로퍼티 | `@property:Description("ID")` |
 * | 백킹 필드 | `@field:Description("ID")` |
 * | getter / setter | `@get:Description("ID")`, `@set:Description("ID")` (`var`) |
 * | Java 필드 · getter · setter · record component | `@Description("ID") private Long id;` |
 *
 * 생성자 파라미터(`VALUE_PARAMETER`)는 대상에서 일부러 뺐다. 넣으면 Kotlin 2.2 이상에서 use-site target 을 생략한
 * 모든 생성자 프로퍼티에 "앞으로 프로퍼티에도 적용된다" 경고가 난다. 생략하면 프로퍼티에 붙으므로 기능 차이는 없다.
 *
 * @property value 필드에 대한 설명 문자열
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Description(val value: String)
