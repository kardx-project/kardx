package io.github.kardx.extractor

import io.github.kardx.dsl.RestDocsField
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.restdocs.payload.JsonFieldType
import java.io.File
import java.net.InetAddress
import java.net.URL
import java.nio.file.Path
import java.sql.Timestamp
import java.util.GregorianCalendar

/** Jackson 이 문자열로 쓰는 JDK 값 타입. `Path` 는 `Iterable<Path>` 라 컬렉션으로 오인하면 원소가 자기 자신이다. */
data class FileInfo(
    val name: String,
    val location: Path,
    val file: File,
    val url: URL,
    val host: InetAddress,
    val at: Timestamp,
    val calendar: GregorianCalendar,
)

data class PathsHolder(val paths: List<Path>)

/** 자기 자신을 원소로 갖는 Iterable */
class TreeNode(val name: String) : Iterable<TreeNode> {
    override fun iterator(): Iterator<TreeNode> = emptyList<TreeNode>().iterator()
}

data class TreeHolder(val root: TreeNode)

/** 원소가 다시 자기 자신의 목록인 컬렉션 */
class Grid : ArrayList<List<Grid>>()

data class GridHolder(val grid: Grid)

private fun List<RestDocsField>.paths() = map { it.descriptor.path }

private fun List<RestDocsField>.typeOf(path: String) = first { it.descriptor.path == path }.descriptor.type

class JdkValueTypesTest :
    BehaviorSpec({
        given("Path·File·URL·InetAddress 와 Date·Calendar 하위 클래스 프로퍼티") {
            then("Jackson 처럼 문자열 값으로 문서화한다") {
                val fields = responseFieldsOf<FileInfo>()

                fields.paths() shouldContainExactly listOf("name", "location", "file", "url", "host", "at", "calendar")
                fields.paths().forEach { fields.typeOf(it) shouldBe JsonFieldType.STRING }
            }
        }

        given("Path 목록") {
            then("문자열 배열이다") {
                val fields = responseFieldsOf<PathsHolder>()

                fields.paths() shouldContainExactly listOf("paths")
                fields.typeOf("paths") shouldBe JsonFieldType.ARRAY
            }
        }

        given("자기 자신을 원소로 갖는 Iterable 프로퍼티") {
            then("원소를 끝없이 따라가지 않는다") {
                val fields = responseFieldsOf<TreeHolder>()

                fields.paths() shouldContainExactly listOf("root")
                fields.typeOf("root") shouldBe JsonFieldType.ARRAY
            }

            then("본문 전체여도 멈춘다 (원소 타입을 모르는 루트 배열이라 줄이 없다)") {
                responseFieldsOf<TreeNode>().shouldBeEmpty()
            }
        }

        given("원소가 다시 자기 자신의 목록인 컬렉션") {
            then("배열 겹수가 최대 깊이에 이르면 멈춘다") {
                val fields = responseFieldsOf<GridHolder>()

                fields.first().descriptor.path shouldBe "grid"
                fields.last().descriptor.path shouldBe "grid" + "[]".repeat(10)
            }
        }
    })
