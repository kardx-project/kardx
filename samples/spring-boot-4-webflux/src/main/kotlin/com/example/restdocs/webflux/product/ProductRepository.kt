package com.example.restdocs.webflux.product

import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * R2DBC 의 `ReactiveCrudRepository` 처럼 Reactor 타입을 돌려주는 메모리 저장소.
 * 컨트롤러가 이 타입을 그대로 돌려주거나(`Mono`) 코루틴으로 기다리는(`awaitSingle`, `asFlow`) 모습을 보이려고 둔다.
 * 데이터는 앱이 뜰 때마다 처음 상태로 돌아간다.
 */
@Repository
class ProductRepository {

    private val products = ConcurrentHashMap(SEED.associateBy { it.id })
    private val sequence = AtomicLong(SEED.maxOf { it.id })

    fun findAll(): Flux<ProductResponse> = Flux.defer { Flux.fromIterable(products.values.sortedBy { it.id }) }

    fun findById(id: Long): Mono<ProductResponse> = Mono.defer { Mono.justOrEmpty(products[id]) }

    fun save(request: CreateProductRequest): Mono<ProductResponse> = Mono.fromCallable {
        val product = ProductResponse(
            id = sequence.incrementAndGet(),
            name = request.name,
            price = request.price,
            status = ProductStatus.SALE,
            sellerPhone = request.sellerPhone.maskPhone(),
        )

        products[product.id] = product

        product
    }

    /** 지웠으면 true, 없는 상품이면 false. */
    fun deleteById(id: Long): Mono<Boolean> = Mono.fromCallable { products.remove(id) != null }

    private companion object {
        val SEED = listOf(
            ProductResponse(1, "기계식 키보드", 59000, ProductStatus.SALE, "010-****-5678"),
            ProductResponse(2, "무선 마우스", 32000, ProductStatus.SOLD_OUT, "010-****-1234"),
            ProductResponse(3, "모니터 받침대", 45000, ProductStatus.SALE, "010-****-9012"),
        )
    }
}
