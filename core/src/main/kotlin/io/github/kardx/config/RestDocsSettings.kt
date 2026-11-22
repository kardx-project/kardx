package io.github.kardx.config

/**
 * 문서화 설정 값. 설정을 주제별 묶음으로 나눠 둔다. 묶음마다 같은 이름의 블록으로 바꾸거나(`openApi { enabled = false }`)
 * 프로퍼티로 바로 바꾼다(`openApi.enabled = false`).
 *
 * 직접 만들지 않고 [RestDocsConfiguration.configure] (전역 기본값) 또는
 * [RestDocsConfiguration.withSettings] (블록 안에서만 적용) 로 바꾼다. yml(`kardx.*`) 키는 묶음과 상관없이 그대로다.
 *
 * ```kotlin
 * RestDocsConfiguration.configure {
 *     language = DocsLanguage.KO
 *     extraction { responseWrapper<ApiResponse<*>>(dataPath = "data") }
 *     masking { maskSensitive() }
 *     openApi { errorSchema<ErrorResponse>() }
 *     document { commonIncludeFiles = listOf("landing.adoc") }
 * }
 * ```
 */
@RestDocsSettingsDsl
public class RestDocsSettings internal constructor() {
    /** 필드·파라미터 추출 (응답 래퍼, Jackson 매퍼, 이름 규칙, 깊이, kotlinx.serialization, Enum 설명 프로퍼티). */
    public var extraction: ExtractionSettings = ExtractionSettings()
        private set

    /** 설명 (설명 규칙과 기본 단계, 설명 어노테이션, KDoc·Javadoc). */
    public var descriptions: DescriptionSettings = DescriptionSettings()
        private set

    /** 가리기 (헤더·쿠키·쿼리·폼 파라미터 값, 마스킹 표시 어노테이션). */
    public var masking: MaskingSettings = MaskingSettings()
        private set

    /** HTTP 문서 가공 (전처리기, 뺄 응답 헤더, 요청 출력, Spring Security 필터). */
    public var http: HttpSettings = HttpSettings()
        private set

    /** OpenAPI (켜기, 보안 스키마, 전역 헤더, 오류·스키마 이름, operationId). */
    public var openApi: OpenApiSettings = OpenApiSettings()
        private set

    /** AsyncAPI 메타데이터. */
    public var asyncApi: AsyncApiSettings = AsyncApiSettings()
        private set

    /** 산출물 위치 (스니펫·AsciiDoc 디렉터리, WireMock 매핑). */
    public var output: OutputSettings = OutputSettings()
        private set

    /** 생성 문서 모양 (include 할 스니펫과 순서, 섹션 순서, index 링크, 공통 include). */
    public var document: DocumentSettings = DocumentSettings()
        private set

    /**
     * 라이브러리가 생성 문서·스펙·뷰어에 넣는 문구의 언어. 기본값 [DocsLanguage.EN].
     * 문서 목록(index) 제목·링크 글자, [DocumentSettings.indexLinkText] 의 기본값, 스키마 설명 접두사, 자동 설명(Spring Data 페이지·
     * `ProblemDetail`·타입 구분자), AsyncAPI 채널 설명과 뷰어 화면이 따른다. 로그·경고·예외 메시지는 언제나 영어다.
     * yml: `kardx.language` (`en`, `ko`). Gradle 플러그인을 쓰면 플러그인의 같은 설정이 시스템 속성으로 넘어온다.
     *
     * ```kotlin
     * language = DocsLanguage.KO
     * ```
     */
    public var language: DocsLanguage = DocsLanguage.EN

    /** [extraction] 묶음을 바꾼다. `extraction { maxDepth = 5 }` */
    public fun extraction(block: ExtractionSettings.() -> Unit) {
        extraction.block()
    }

    /** [descriptions] 묶음을 바꾼다. */
    public fun descriptions(block: DescriptionSettings.() -> Unit) {
        descriptions.block()
    }

    /** [masking] 묶음을 바꾼다. */
    public fun masking(block: MaskingSettings.() -> Unit) {
        masking.block()
    }

    /** [http] 묶음을 바꾼다. */
    public fun http(block: HttpSettings.() -> Unit) {
        http.block()
    }

    /** [openApi] 묶음을 바꾼다. */
    public fun openApi(block: OpenApiSettings.() -> Unit) {
        openApi.block()
    }

    /** [asyncApi] 묶음을 바꾼다. */
    public fun asyncApi(block: AsyncApiSettings.() -> Unit) {
        asyncApi.block()
    }

    /** [output] 묶음을 바꾼다. */
    public fun output(block: OutputSettings.() -> Unit) {
        output.block()
    }

    /** [document] 묶음을 바꾼다. */
    public fun document(block: DocumentSettings.() -> Unit) {
        document.block()
    }

    internal fun copy(): RestDocsSettings = RestDocsSettings().also {
        it.language = language
        it.extraction = extraction.copy()
        it.descriptions = descriptions.copy()
        it.masking = masking.copy()
        it.http = http.copy()
        it.openApi = openApi.copy()
        it.asyncApi = asyncApi.copy()
        it.output = output.copy()
        it.document = document.copy()
    }
}
