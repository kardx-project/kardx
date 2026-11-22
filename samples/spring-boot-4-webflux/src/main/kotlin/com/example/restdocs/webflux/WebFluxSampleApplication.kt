package com.example.restdocs.webflux

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebFluxSampleApplication

fun main(args: Array<String>) {
    runApplication<WebFluxSampleApplication>(*args)
}
