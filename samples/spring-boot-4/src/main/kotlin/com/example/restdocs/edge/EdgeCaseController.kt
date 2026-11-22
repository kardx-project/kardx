package com.example.restdocs.edge

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import com.example.restdocs.edge.cart.Item as CartItem
import com.example.restdocs.edge.order.Item as OrderItem

/**
 * 문서화가 까다로운 모양을 모은 API. 정규식 경로 변수, 이름이 같은 다른 패키지의 클래스,
 * 요청·응답 모양이 다른 DTO, Spring Data `Page` 응답.
 */
@RestController
@RequestMapping("/api/v1/edge")
class EdgeCaseController {
    // 경로 변수에 점(`.`)이 들어가도록 정규식을 쓴다.
    @GetMapping("/files/{name:.+}")
    fun file(@PathVariable name: String): FileInfo = FileInfo(name, name.length * FILE_SIZE_UNIT)

    @GetMapping("/orders/{id}/item")
    fun orderItem(@PathVariable id: Long): OrderItem = OrderItem(id, "기계식 키보드")

    @GetMapping("/carts/{id}/item")
    fun cartItem(@PathVariable id: Long): CartItem = CartItem("KB-$id", 2)

    @PostMapping("/accounts")
    fun createAccount(@RequestBody account: Account): Account = account.copy(createdAt = "2026-11-12T09:00:00")

    @GetMapping("/articles")
    fun articles(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "2") size: Int,
        @RequestParam(required = false) sort: String?,
    ): Page<Article> {
        val order = sort?.let {
            val parts = it.split(',')
            val direction = parts.getOrNull(1)?.let(Sort.Direction::fromString) ?: Sort.Direction.ASC

            Sort.by(direction, parts.first())
        } ?: Sort.unsorted()

        return PageImpl(listOf(Article(1, "첫 글"), Article(2, "둘째 글")), PageRequest.of(page, size, order), TOTAL)
    }

    private companion object {
        const val FILE_SIZE_UNIT = 1024L
        const val TOTAL = 10L
    }
}
