package com.example.books

import io.github.kardx.dsl.pathMeans
import io.github.kardx.extractor.requestFields
import io.github.kardx.extractor.responseFields
import io.github.kardx.junit.AutoRestDocumentationExtension
import io.github.kardx.mockmvc.andDocument
import io.github.kardx.mockmvc.andStatus
import io.github.kardx.mockmvc.jsonContent
import io.github.kardx.mockmvc.restDocsMockMvc
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.web.context.WebApplicationContext

// Maven 에서도 Gradle 과 같은 테스트다. 스니펫은 target/generated-snippets 에 생긴다
@DisplayName("Books")
@ExtendWith(AutoRestDocumentationExtension::class)
@SpringBootTest
class BookApiDocsTest {
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(context: WebApplicationContext, provider: RestDocumentationContextProvider) {
        mockMvc = restDocsMockMvc(context, provider)
    }

    @Test
    @DisplayName("List books")
    fun listBooks() {
        mockMvc.perform(get("/api/books"))
            .andStatus(200)
            .andDocument {
                description = "Returns every book, oldest first."
                responseFields<List<BookResponse>>()
            }
    }

    @Test
    @DisplayName("Get a book")
    fun getBook() {
        mockMvc.perform(get("/api/books/{id}", 1L))
            .andStatus(200)
            .andDocument {
                description = "Returns one book by ID."
                pathParameters("id" pathMeans "Book ID")
                responseFields<BookResponse>()
            }
    }

    @Test
    @DisplayName("Add a book")
    fun createBook() {
        mockMvc.perform(post("/api/books").jsonContent(CreateBookRequest("Refactoring", "Martin Fowler", 448)))
            .andStatus(201)
            .andDocument {
                description = "Adds a book and returns it with its new ID."
                requestFields<CreateBookRequest>()
                responseFields<BookResponse>()
            }
    }
}
