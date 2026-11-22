package io.github.kardx.internal.http
import io.github.kardx.config.MaskLocation
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.config.RestDocsSettings
import io.github.kardx.config.ValueMasker
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/** 가리기 결과를 보려는 가짜 Authorization 값. 실제 자격증명이 아니다. */
private const val FAKE_BEARER = "Bearer secret"

private val maskAuthAndCookies: RestDocsSettings.() -> Unit = { masking.maskSensitive() }

class HeaderMaskingTest :
    BehaviorSpec({
        given("가릴 헤더를 정하지 않은 기본 설정") {
            then("아무것도 가리지 않는다") {
                HeaderMasking.isMasked("Authorization") shouldBe false
                HeaderMasking.mask("Authorization", FAKE_BEARER) shouldBe FAKE_BEARER
            }

            then("maskAlways 는 설정과 상관없이 가린다") {
                HeaderMasking.maskAlways("Authorization", FAKE_BEARER) shouldBe "Bearer ****"
                HeaderMasking.maskAlways("X-Api-Key", "key-123") shouldBe "****"
                HeaderMasking.maskAlways("Cookie", "SESSION=abc") shouldBe "SESSION=****"
            }
        }

        given("민감 헤더를 가리는 설정") {
            then("Authorization 은 인증 방식을 남기고 값만 가린다") {
                RestDocsConfiguration.withSettings(maskAuthAndCookies) {
                    HeaderMasking.mask("Authorization", FAKE_BEARER) shouldBe "Bearer ****"
                    HeaderMasking.mask("Proxy-Authorization", "Basic dXNlcjpwdw==") shouldBe "Basic ****"
                    // 방식이 없는 값은 통째로 가린다
                    HeaderMasking.mask("Authorization", "secret") shouldBe "****"
                }
            }

            then("Cookie 는 쿠키마다 이름을 남기고 값만 가린다") {
                RestDocsConfiguration.withSettings(maskAuthAndCookies) {
                    HeaderMasking.mask("Cookie", "SESSION=abc; theme=dark") shouldBe "SESSION=****; theme=****"
                }
            }

            then("Set-Cookie 는 쿠키 값만 가리고 속성(Path, HttpOnly 등)은 남긴다") {
                RestDocsConfiguration.withSettings(maskAuthAndCookies) {
                    HeaderMasking.mask("Set-Cookie", "SESSION=abc; Path=/; HttpOnly") shouldBe
                        "SESSION=****; Path=/; HttpOnly"
                }
            }

            then("헤더 이름의 대소문자를 무시한다") {
                RestDocsConfiguration.withSettings(maskAuthAndCookies) {
                    HeaderMasking.isMasked("authorization") shouldBe true
                    HeaderMasking.mask("AUTHORIZATION", FAKE_BEARER) shouldBe "Bearer ****"
                    HeaderMasking.mask("cookie", "a=1") shouldBe "a=****"
                    HeaderMasking.mask("set-cookie", "a=1; Secure") shouldBe "a=****; Secure"
                }
            }

            then("가릴 헤더가 아니면 그대로 둔다") {
                RestDocsConfiguration.withSettings(maskAuthAndCookies) {
                    HeaderMasking.mask("Content-Type", "application/json") shouldBe "application/json"
                }
            }
        }

        given("가린 값과 마스커를 바꾼 설정") {
            then("masking.replacement 를 쓴다") {
                RestDocsConfiguration.withSettings({
                    masking.headers = setOf("X-Api-Key", "Authorization")
                    masking.replacement = "[hidden]"
                }) {
                    HeaderMasking.mask("x-api-key", "key-123") shouldBe "[hidden]"
                    HeaderMasking.mask("Authorization", FAKE_BEARER) shouldBe "Bearer [hidden]"
                }
            }

            then("masking.masker 가 있으면 인증 방식도 마스커에 맡긴다") {
                val masker = ValueMasker { "${it.name}:${it.value.length}" }

                RestDocsConfiguration.withSettings({
                    masking.maskSensitive()
                    masking.masker = masker
                }) {
                    HeaderMasking.mask("Authorization", FAKE_BEARER) shouldBe "Authorization:13"
                    HeaderMasking.mask("Cookie", "SESSION=abc") shouldBe "SESSION=Cookie:3"
                }
            }

            then("마스커는 값의 위치(헤더·쿠키·파라미터)와 쿠키 이름을 받는다") {
                val seen = mutableListOf<String>()
                val masker = ValueMasker { target ->
                    seen += "${target.location}:${target.name}:${target.cookieName}"
                    "x"
                }

                RestDocsConfiguration.withSettings({
                    masking.maskSensitive()
                    masking.masker = masker
                }) {
                    HeaderMasking.mask("Authorization", FAKE_BEARER)
                    HeaderMasking.mask("Set-Cookie", "SESSION=abc; Path=/") shouldBe "SESSION=x; Path=/"
                    HeaderMasking.maskParameter("access_token", "t")
                }

                seen shouldBe listOf(
                    "${MaskLocation.HEADER}:Authorization:null",
                    "${MaskLocation.COOKIE}:Set-Cookie:SESSION",
                    "${MaskLocation.PARAMETER}:access_token:null",
                )
            }
        }
    })
