package com.example.restdocs.webflux.common

/** 요청한 리소스가 없을 때. 404 로 응답한다. */
class NotFoundException(message: String) : RuntimeException(message)
