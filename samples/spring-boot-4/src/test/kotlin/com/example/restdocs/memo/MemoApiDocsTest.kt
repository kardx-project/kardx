package com.example.restdocs.memo

import com.example.restdocs.support.sampleDocsExtension
import io.github.kardx.ExperimentalRestDocsApi
import io.github.kardx.dsl.FromParamTag
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.queryParameters
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.LocalDateTime

// KDoc 만으로 문서를 만드는 예제. 이 테스트는 설명을 한 줄도 적지 않는다 (description·summary·필드 설명 없음).
// - 필드·쿼리 파라미터·enum 값 설명: com.example.restdocs.memo 의 DTO KDoc (프로퍼티 KDoc, 클래스 @property)
// - 경로 변수·쿼리 파라미터 설명: MemoController 메서드 KDoc 의 @param. 테스트는 pathParameters("id") 로 이름만 적고,
//   수식어가 필요한 includeReminder 는 설명 자리에 FromParamTag 를 둔다
// - summary·description: MemoController 메서드 KDoc 의 첫 문장과 전체. OpenAPI 와 HTML 문서 모두에 들어간다
//   (build 파일의 sourceDocs { methodDocs = true })
// @DisplayName 은 테스트 이름이다. 문서의 섹션 제목과 OpenAPI 태그가 되고, operation summary 는 메서드 KDoc 이 먼저다
// 기본 클래스(RestDocsTestSupport) 없이 쓰는 예제이기도 하다. 문서화 확장이 테스트 메서드의 MockMvc 파라미터에 REST Docs 용
// MockMvc(restDocsMockMvc(context, provider) 와 같은 것)를 넣으므로 @BeforeEach 가 필요 없다

/**
 * KDoc 만으로 문서화하는 예제.
 *
 * | 테스트 | 설명이 오는 곳 |
 * |---|---|
 * | createMemo | 요청: 프로퍼티 KDoc, 중첩 `MemoReminder`, `MemoColor` 상수 KDoc. 응답: `MemoResponse` 의 `@property` |
 * | getMemo | 경로 변수·쿼리 파라미터: `MemoController.getMemo` KDoc 의 `@param` (`pathParameters("id")`, `FromParamTag`) |
 * | listMemos | 쿼리 파라미터: `MemoSearchCondition` 프로퍼티 KDoc. 응답: `MemoPage` 의 `@property` |
 */
@OptIn(ExperimentalRestDocsApi::class) // KDoc @param 으로 설명을 채우는 pathParameters("id")·FromParamTag 는 실험 기능이다
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("메모 API")
class MemoApiDocsTest {
    companion object {
        @JvmField
        @RegisterExtension
        val restDocs = sampleDocsExtension()
    }

    @Test
    @DisplayName("메모 작성")
    fun createMemo(mockMvc: MockMvc) {
        mockMvc.perform(
            post("/api/v1/memos").jsonContent(
                CreateMemoRequest(
                    title = "병원 예약",
                    content = "오전 10시 치과",
                    color = MemoColor.PINK,
                    tags = listOf("건강"),
                    reminder = MemoReminder(LocalDateTime.of(2026, 11, 21, 9, 0), daily = false),
                ),
            ),
        ).andStatus(HttpStatus.CREATED)
            .andDocument {
                requestFields<CreateMemoRequest>()
                responseFields<MemoResponse>()
            }
    }

    @Test
    @DisplayName("메모 조회")
    fun getMemo(mockMvc: MockMvc) {
        mockMvc.perform(get("/api/v1/memos/{id}", 1L))
            .andStatus(HttpStatus.OK)
            .andDocument {
                pathParameters("id")
                queryParameters("includeReminder" queryMeans FromParamTag isOptional true example "false")
                responseFields<MemoResponse>()
            }
    }

    @Test
    @DisplayName("메모 목록 조회")
    fun listMemos(mockMvc: MockMvc) {
        mockMvc.perform(
            get("/api/v1/memos")
                .queryParam("color", MemoColor.YELLOW.name)
                .queryParam("page", "0")
                .queryParam("size", "20"),
        ).andStatus(HttpStatus.OK)
            .andDocument {
                queryParameters<MemoSearchCondition>()
                responseFields<MemoPage>()
            }
    }
}
