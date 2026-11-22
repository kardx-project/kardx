package com.example.restdocs.memo

import com.example.restdocs.common.NotFoundException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// KDoc 만으로 문서를 만드는 예제 (MemoApiDocsTest). 이 패키지에는 @Description 같은 설명 어노테이션이 없다.
// - 필드·쿼리 파라미터·enum 상수 설명: DTO 의 KDoc (apiDocsSourceDocs 가 읽어 source-docs.json 에 쓴다)
// - 경로 변수·쿼리 파라미터 설명: 아래 메서드 KDoc 의 @param (테스트는 pathParameters("id") 처럼 이름만 적거나,
//   수식어가 필요하면 "includeReminder" queryMeans FromParamTag isOptional true 로 적는다. methodDocs 와 상관없다)
// - summary·description: 아래 메서드 KDoc 의 첫 문장과 전체. OpenAPI 와 HTML 문서(api-summary) 모두에 들어간다
//   (build 파일의 sourceDocs { methodDocs = true })
// 응답은 공통 래퍼(RestResponse)로 감싸지 않는다. 래퍼의 설명은 @Description 이라 KDoc 만의 예제가 되지 않는다

/** 메모 API. 메모를 메모리에만 저장한다. */
@RestController
@RequestMapping("/api/v1/memos")
class MemoController {

    private val memos = ConcurrentHashMap(SEED.associateBy { it.id })
    private val nextId = AtomicLong(SEED.size.toLong())

    /**
     * 새 메모를 작성한다.
     *
     * 저장한 메모를 ID 와 작성 시각을 채워 돌려준다. 색을 보내지 않으면 노란색(YELLOW) 메모가 된다.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMemo(@RequestBody @Valid request: CreateMemoRequest): MemoResponse {
        val memo = MemoResponse(
            id = nextId.incrementAndGet(),
            title = request.title,
            content = request.content,
            color = request.color,
            tags = request.tags,
            reminder = request.reminder,
            createdAt = CREATED_AT,
        )
        memos[memo.id] = memo

        return memo
    }

    /**
     * ID 로 메모 하나를 조회한다.
     *
     * 알림을 정하지 않은 메모는 `reminder` 가 `null` 이다. 없는 ID 면 404 를 돌려준다.
     *
     * @param id 조회할 메모의 ID
     * @param includeReminder 알림을 함께 돌려줄지 여부. `false` 면 `reminder` 를 `null` 로 돌려준다
     */
    @GetMapping("/{id}")
    fun getMemo(@PathVariable id: Long, @RequestParam(defaultValue = "true") includeReminder: Boolean): MemoResponse {
        val memo = memos[id] ?: throw NotFoundException("메모를 찾을 수 없습니다: $id")

        return if (includeReminder) memo else memo.copy(reminder = null)
    }

    /**
     * 메모 목록을 페이지로 나눠 조회한다.
     *
     * 메모를 ID 순으로 [MemoSearchCondition.page]·[MemoSearchCondition.size] 로 나눠 돌려준다.
     * [color][MemoSearchCondition.color] 를 보내면 그 색의 메모만 돌려준다.
     */
    @GetMapping
    fun listMemos(@ModelAttribute condition: MemoSearchCondition): MemoPage {
        val matched = memos.values.filter { condition.color == null || it.color == condition.color }
            .sortedBy { it.id }

        return MemoPage(
            total = matched.size,
            page = condition.page,
            size = condition.size,
            content = matched.drop(condition.page * condition.size).take(condition.size),
        )
    }

    private companion object {
        val CREATED_AT: LocalDateTime = LocalDateTime.of(2026, 11, 20, 9, 0)

        val SEED = listOf(
            MemoResponse(
                id = 1,
                title = "장보기",
                content = "우유, 달걀",
                color = MemoColor.YELLOW,
                tags = listOf("집"),
                reminder = MemoReminder(LocalDateTime.of(2026, 11, 20, 18, 0)),
                createdAt = CREATED_AT,
            ),
            MemoResponse(
                id = 2,
                title = "회의 준비",
                content = "발표 자료 검토",
                color = MemoColor.PINK,
                tags = listOf("회사"),
                reminder = null,
                createdAt = CREATED_AT,
            ),
        )
    }
}
