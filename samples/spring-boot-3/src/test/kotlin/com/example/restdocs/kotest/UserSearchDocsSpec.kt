package com.example.restdocs.kotest

import com.example.restdocs.dto.UserResponse
import com.example.restdocs.support.DocsConfig
import io.github.kardx.annotation.AsciiDescription
import io.github.kardx.dsl.queryMeans
import io.github.kardx.extractor.responseFields
import io.github.kardx.kotest.RestDocsExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.restDocsMockMvc
import io.kotest.core.annotation.DisplayName
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.web.context.WebApplicationContext

/**
 * Kotest 로 쓰는 문서화 테스트 예. JUnit 테스트(RestDocsTestSupport)와 같은 test 태스크에서 돌고, 문서는 index·HTML·OpenAPI 에
 * JUnit 테스트 문서와 나란히 들어간다.
 *
 * - Kotest Spring 확장이 생성자에 컨텍스트를 넣고, yml(`application-docs.yml`)의 `kardx.*` 은 확장이 읽는다
 * - 문서 제목은 스펙의 `@DisplayName`, 섹션 제목은 `given - when - then` 이름, 스니펫 디렉터리는 그 이름을 다듬은 값이다
 * - `beforeEach` 에서 한 코드 설정([DocsConfig])은 테스트가 끝나면 확장이 되돌린다
 * - OpenAPI operationId 는 기본으로 컨트롤러 메서드 이름이다. 여기서는 `openApi { operationId }` 로 직접 정한다
 * - 확장은 프로젝트 설정([io.kotest.provided.ProjectConfig])에도 등록돼 있다. 양쪽에 등록하면 스펙에 등록한 확장이 문서화하고
 *   문서도 그 확장이 한 번만 쓴다. 그래서 스펙 하나만 생성자 인자(`sectionTitle`, `documentGenerator` 등)를 바꿀 수 있다
 */
@ApplyExtension(SpringExtension::class)
@SpringBootTest
@ActiveProfiles("docs")
@DisplayName("사용자 검색 API (Kotest)")
@AsciiDescription("Kotest BehaviorSpec 으로 문서화")
class UserSearchDocsSpec(context: WebApplicationContext) :
    BehaviorSpec({
        val restDocs = extension(RestDocsExtension())
        val mockMvc = restDocsMockMvc(context, restDocs.provider)

        beforeEach { DocsConfig.apply() }

        given("가입한 사용자가 있을 때") {
            `when`("닉네임으로 검색하면") {
                then("그 사용자 목록을 돌려준다") {
                    mockMvc.perform(get("/api/v1/users/search").param("nickname", "홍길동"))
                        .andStatus(HttpStatus.OK)
                        .andExpect(jsonPath("$[0].id").value(1))
                        .andDocument {
                            description = "닉네임이 같은 사용자를 찾는다."
                            queryParameters("nickname" queryMeans "찾을 닉네임")
                            responseFields<List<UserResponse>>()
                            openApi { operationId = "searchUsersByNickname" }
                        }
                }
            }

            `when`("다른 사용자의 닉네임으로 검색하면") {
                then("그 사용자만 돌려준다") {
                    mockMvc.perform(get("/api/v1/users/search").param("nickname", "김철수"))
                        .andStatus(HttpStatus.OK)
                        .andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].id").value(2))
                        .andDocument {
                            queryParameters("nickname" queryMeans "찾을 닉네임")
                            responseFields<List<UserResponse>>()
                        }
                }
            }
        }
    })
