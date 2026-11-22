package com.example.restdocs.webflux.product

import com.example.restdocs.webflux.common.NotFoundException
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.net.URI

/**
 * WebFlux 어노테이션 컨트롤러. 반환 타입을 섞어 쓴다.
 *
 * | 핸들러 | 반환 |
 * |---|---|
 * | list | `Flow` (WebFlux 가 JSON 배열로 쓴다) |
 * | get, summary, create | 코루틴 `suspend` 함수 |
 * | delete | Reactor `Mono` |
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductController(private val repository: ProductRepository) {

    @GetMapping
    fun list(@RequestParam(required = false) status: ProductStatus?): Flow<ProductSummary> = repository.findAll()
        .asFlow()
        .filter { status == null || it.status == status }
        .map { it.toSummary() }

    @GetMapping("/{id}")
    suspend fun get(@PathVariable id: Long): ProductResponse = repository.findById(id)
        .awaitSingleOrNull()
        ?: throw NotFoundException("상품을 찾을 수 없습니다: $id")

    // 상품 요약. Kotest 스펙(ProductSummaryDocsSpec)이 문서화한다 (KDoc 이면 OpenAPI summary 가 테스트 이름 대신 이 문장이 된다)
    @GetMapping("/{id}/summary")
    suspend fun summary(@PathVariable id: Long): ProductSummary = get(id).toSummary()

    @PostMapping
    suspend fun create(@RequestBody @Valid request: CreateProductRequest): ResponseEntity<ProductResponse> {
        val saved = repository.save(request)
            .awaitSingle()

        return ResponseEntity.created(URI.create("/api/v1/products/${saved.id}"))
            .body(saved)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): Mono<ResponseEntity<Void>> = repository.deleteById(id)
        .map { deleted -> if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build() }
}
