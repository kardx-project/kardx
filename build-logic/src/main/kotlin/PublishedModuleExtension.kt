import org.gradle.api.provider.Property

/**
 * 배포 모듈의 좌표와 설명. 각 모듈의 build 파일이 `publishedModule { ... }` 로 채운다.
 *
 * ```kotlin
 * publishedModule {
 *     artifactId = "kardx"
 *     description = "..."
 *     moduleName = "io.github.kardx"
 * }
 * ```
 */
abstract class PublishedModuleExtension {

    /** 배포 아티팩트 ID. jar 파일 이름과 POM 의 name 으로도 쓴다. */
    abstract val artifactId: Property<String>

    /** POM 의 description. */
    abstract val description: Property<String>

    /** jar manifest 의 `Automatic-Module-Name`. jar 를 내는 모듈(bom 제외)은 반드시 정한다. */
    abstract val moduleName: Property<String>
}
