import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * 두 샘플의 소스 트리(`src`)가 허용한 차이 말고는 같은지 확인한다.
 *
 * 샘플은 같은 코드가 Spring Boot 3 / 4 에서 똑같이 도는지 보이려고 소스를 두 벌 둔다. 공유 소스 디렉터리를 두 모듈에
 * 함께 등록하면 IntelliJ 가 한 모듈에만 붙여 다른 쪽 편집 지원이 끊기고, 샘플 하나만 복사해 시작하기도 어려워진다.
 * 그래서 복사본을 두고 이 검사로 어긋남을 막는다.
 *
 * 허용 목록이 실제와 맞지 않아도(이제 같아졌거나 사라진 파일) 실패한다. 목록이 낡지 않게 하기 위해서다.
 */
@DisableCachingByDefault(because = "파일 비교만 하므로 캐시해서 얻을 것이 없다")
abstract class SampleSourcesSyncCheck : DefaultTask() {

    /** 첫째 샘플의 소스 디렉터리 (예: samples/spring-boot-3/src). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val firstSources: DirectoryProperty

    /** 둘째 샘플의 소스 디렉터리 (예: samples/spring-boot-4/src). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val secondSources: DirectoryProperty

    /** 양쪽에 있지만 내용이 달라도 되는 파일. 소스 디렉터리 기준 상대 경로. */
    @get:Input
    abstract val allowedDifferences: SetProperty<String>

    /** 첫째 샘플에만 있어도 되는 파일. */
    @get:Input
    abstract val onlyInFirst: SetProperty<String>

    /** 둘째 샘플에만 있어도 되는 파일. */
    @get:Input
    abstract val onlyInSecond: SetProperty<String>

    /** 검사 통과 표시. 입력이 그대로면 태스크가 UP-TO-DATE 가 되게 한다. */
    @get:OutputFile
    abstract val result: RegularFileProperty

    /** 파일 트리를 만든다. Gradle 기본 제외(`.DS_Store`, `.git`, 편집기 임시 파일 등)가 적용된다. */
    @get:Inject
    protected abstract val objects: ObjectFactory

    @TaskAction
    fun check() {
        val first = firstSources.get().asFile
        val second = secondSources.get().asFile
        val firstFiles = relativeFiles(first)
        val secondFiles = relativeFiles(second)
        val common = firstFiles intersect secondFiles
        val differing = common.filterNot { sameContent(File(first, it), File(second, it)) }
            .toSet()

        val problems = buildList {
            addAll(unexpected("${first.name} 쪽에만 있다", firstFiles - secondFiles, onlyInFirst.get()))
            addAll(unexpected("${second.name} 쪽에만 있다", secondFiles - firstFiles, onlyInSecond.get()))
            addAll(unexpected("내용이 다르다", differing, allowedDifferences.get()))
            addAll(stale("onlyInFirst", onlyInFirst.get(), firstFiles - secondFiles))
            addAll(stale("onlyInSecond", onlyInSecond.get(), secondFiles - firstFiles))
            addAll(stale("allowedDifferences", allowedDifferences.get(), differing))
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                """
                |두 샘플의 소스가 어긋났습니다. 같은 변경을 양쪽에 하거나, 의도한 차이면 루트 build.gradle.kts 의 허용 목록을 고치세요.
                |  ${first.path}
                |  ${second.path}
                |${problems.joinToString("\n") { "  - $it" }}
                """.trimMargin(),
            )
        }

        result.get().asFile.writeText("${common.size} files in sync\n")
    }

    /** [root] 아래 파일의 상대 경로. OS 가 만드는 파일(`.DS_Store`)은 Gradle 기본 제외로 빠진다. */
    private fun relativeFiles(root: File): Set<String> {
        val files = mutableSetOf<String>()

        objects.fileTree()
            .from(root)
            .visit {
                if (!isDirectory) {
                    files += relativePath.pathString
                }
            }

        return files
    }

    private fun sameContent(a: File, b: File): Boolean = Files.mismatch(a.toPath(), b.toPath()) == -1L

    private fun unexpected(reason: String, found: Set<String>, allowed: Set<String>): List<String> = (found - allowed)
        .sorted()
        .map { "$reason: $it" }

    private fun stale(listName: String, allowed: Set<String>, found: Set<String>): List<String> = (allowed - found)
        .sorted()
        .map { "$listName 에 있지만 해당하지 않는다 (목록에서 지울 것): $it" }
}
