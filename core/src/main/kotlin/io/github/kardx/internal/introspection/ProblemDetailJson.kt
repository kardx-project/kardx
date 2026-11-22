package io.github.kardx.internal.introspection

import io.github.kardx.internal.i18n.DocsText
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Spring `ProblemDetail`(RFC 9457)이 JSON 으로 나가는 모양.
 *
 * Spring 이 매퍼에 등록하는 mixin 은 비어 있는 값을 빼고(Spring 7 은 기본 `type` 인 `about:blank` 도 뺀다)
 * `properties` 맵을 이름 없이 부모 객체에 펼친다. 그래서 모든 필드가 optional 이고, `properties` 는 필드가 아니다.
 * 확장 항목이 더 나올 수 있으므로 추상 클래스로 두어 [isOpenType] 으로 다룬다 (다른 객체 안에 있으면 subsection).
 * 값을 담지 않으므로 인스턴스는 만들지 않는다.
 */
internal abstract class ProblemDetailJson(
    @BuiltInDescription(DocsText.PROBLEM_TYPE) val type: String?,
    @BuiltInDescription(DocsText.PROBLEM_TITLE) val title: String?,
    @BuiltInDescription(DocsText.PROBLEM_STATUS) val status: Int?,
    @BuiltInDescription(DocsText.PROBLEM_DETAIL) val detail: String?,
    @BuiltInDescription(DocsText.PROBLEM_INSTANCE) val instance: String?,
)

/** [type] 이 Spring `ProblemDetail` 이면 [ProblemDetailJson] 타입. spring-web 에 컴파일 의존하지 않도록 이름으로 본다. */
internal fun problemDetailType(type: KType): KType? {
    val k = type.classifier as? KClass<*> ?: return null

    return if (k.java.name == PROBLEM_DETAIL) typeOf<ProblemDetailJson>() else null
}

private const val PROBLEM_DETAIL = "org.springframework.http.ProblemDetail"
