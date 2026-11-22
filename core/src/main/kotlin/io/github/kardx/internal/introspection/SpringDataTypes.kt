package io.github.kardx.internal.introspection

import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.internal.jackson.mixInClassFor
import io.github.kardx.internal.reflect.loadClassOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf

/**
 * Spring Data 타입(`Page`, `Slice`, `Sort`, `Pageable`)을 Jackson·Spring MVC 가 실제로 쓰는 모양의 타입으로 바꾼다.
 *
 * `Page`·`Slice`·`Sort` 는 `Iterable` 이지만 Jackson 은 배열이 아니라 객체로 내보내고, `Pageable` 파라미터는
 * 인터페이스의 getter 가 아니라 `page`·`size`·`sort` 쿼리 파라미터로 받는다. spring-data 에 컴파일 의존하지 않도록
 * 클래스 이름으로 판별한다.
 *
 * | 타입 | 본문 | 파라미터 |
 * |---|---|---|
 * | `Page<T>` (`PageImpl` 등) | `content[]`, `pageable`, `totalElements`, `totalPages`, `size`, `number`, `sort` … | - |
 * | `Page<T>` (`VIA_DTO` 모드) | `content[]`, `page.size`, `page.number`, `page.totalElements`, `page.totalPages` | - |
 * | `Slice<T>` | `Page` 에서 `totalElements`, `totalPages` 를 뺀 모양 | - |
 * | `Sort` | `empty`, `sorted`, `unsorted` | `sort` |
 * | `Pageable` | - | `page`, `size`, `sort` |
 *
 * `VIA_DTO` 모드는 매퍼에 Spring Data 가 `PageImpl` 용 mixin 을 등록했는지로 판별한다.
 */
internal object SpringDataTypes {
    private val supertypeNames = ConcurrentHashMap<KClass<*>, Set<String>>()

    /** [type] 이 Spring Data 의 본문 타입이면 JSON 모양 타입, 아니면 null. */
    fun bodyType(type: KType): KType? {
        val k = type.classifier as? KClass<*> ?: return null
        val names = namesOf(k)

        return when {
            PAGE in names -> if (pagedViaDto(k)) {
                shape(SpringDataPagedJson::class, elementOf(type, k))
            } else {
                shape(SpringDataPageJson::class, elementOf(type, k))
            }

            SLICE in names -> shape(SpringDataSliceJson::class, elementOf(type, k))

            SORT in names -> typeOf<SpringDataSortJson>()

            else -> null
        }
    }

    /** [type] 이 컨트롤러 파라미터로 받는 Spring Data 타입이면 쿼리 파라미터 모양 타입, 아니면 null. */
    fun parameterType(type: KType): KType? {
        val k = type.classifier as? KClass<*> ?: return null
        val names = namesOf(k)

        return when {
            PAGEABLE in names -> typeOf<SpringDataPageableParameters>()
            SORT in names -> typeOf<SpringDataSortParameters>()
            else -> null
        }
    }

    private fun namesOf(k: KClass<*>): Set<String> = supertypeNames.getOrPut(k) {
        val name = k.java.name

        // JDK·Kotlin 표준 타입은 Spring Data 타입을 구현하지 않으므로 상위 타입을 계산하지 않는다
        if (name.startsWith(JAVA_PACKAGE) || name.startsWith(KOTLIN_PACKAGE)) {
            emptySet()
        } else {
            (listOf(k) + k.allSuperclasses).map { it.java.name }
                .toSet()
        }
    }

    /** `Slice<T>` 의 `T` 를 [type] 의 타입 인자로 치환한 원소 타입. 알 수 없으면 `Any?`. */
    private fun elementOf(type: KType, k: KClass<*>): KType {
        val slice = (listOf(type) + k.allSupertypes).firstOrNull {
            (it.classifier as? KClass<*>)?.java?.name == SLICE
        }

        return slice?.arguments?.firstOrNull()?.type?.substitute(bindingsOf(type)) ?: typeOf<Any?>()
    }

    private fun shape(shape: KClass<*>, element: KType): KType =
        shape.createType(listOf(KTypeProjection.invariant(element)))

    /** Spring Data 가 `PageImpl` 을 `PagedModel` 로 바꿔 내보내도록 매퍼를 설정했는지. */
    private fun pagedViaDto(k: KClass<*>): Boolean {
        val pageImpl = loadClassOrNull(PAGE_IMPL, k.java.classLoader) ?: return false

        // 매퍼 설정을 리플렉션으로 읽는다. 읽지 못하면 Spring Data 기본값(DIRECT 모드)으로 본다
        return runCatching { mixInClassFor(RestDocsConfiguration.objectMapper(), pageImpl) != null }.getOrDefault(false)
    }

    private const val PAGE = "org.springframework.data.domain.Page"
    private const val PAGE_IMPL = "org.springframework.data.domain.PageImpl"
    private const val SLICE = "org.springframework.data.domain.Slice"
    private const val SORT = "org.springframework.data.domain.Sort"
    private const val PAGEABLE = "org.springframework.data.domain.Pageable"
    private const val JAVA_PACKAGE = "java."
    private const val KOTLIN_PACKAGE = "kotlin."
}
