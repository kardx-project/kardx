package io.github.kardx.extractor

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kardx.config.DocsLanguage
import io.github.kardx.config.RestDocsConfiguration
import io.github.kardx.dsl.ENUM_VALUES_ATTRIBUTE
import io.github.kardx.dsl.RestDocsField
import io.github.kardx.support.verifyResponseFields
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.SubsectionDescriptor
import java.math.BigDecimal
import java.util.Date

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes(
    JsonSubTypes.Type(Vehicle.Car::class, name = "car"),
    JsonSubTypes.Type(Vehicle.Bike::class, name = "bike"),
)
sealed class Vehicle {
    data class Car(val doors: Int) : Vehicle()

    data class Bike(val gears: Int) : Vehicle()
}

data class Garage(val vehicle: Vehicle)

enum class EventType { CREATED, DELETED }

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(Event.Created::class, name = "CREATED"),
    JsonSubTypes.Type(Event.Deleted::class, name = "DELETED"),
)
sealed class Event {
    abstract val type: EventType

    data class Created(val id: Long) : Event() {
        override val type: EventType = EventType.CREATED
    }

    data class Deleted(val id: Long) : Event() {
        override val type: EventType = EventType.DELETED
    }
}

data class Ordinal(@get:JsonFormat(shape = JsonFormat.Shape.NUMBER) val level: EventType)

data class Formatted(
    @get:JsonFormat(shape = JsonFormat.Shape.NUMBER) val at: Date,
    @get:JsonFormat(shape = JsonFormat.Shape.STRING) val price: BigDecimal,
)

class Attributes(val id: Long) {
    @get:JsonAnyGetter
    val extra: Map<String, Any> = mapOf("color" to "red", "size" to 3)
}

data class Product(val attributes: Attributes)

data class Problems(val problem: ProblemDetail)

private fun List<RestDocsField>.byPath() = associateBy { it.descriptor.path }

class JacksonShapesTest :
    BehaviorSpec({
        given("@JsonTypeInfo(include = WRAPPER_OBJECT) sealed 타입") {
            then("하위 타입 이름이 키인 optional 객체로 추출하고 실제 직렬화와 맞는다") {
                // @JsonSubTypes 선언 순서
                responseFieldsOf<Garage>().map { it.descriptor.path } shouldContainExactly
                    listOf("vehicle", "vehicle.car", "vehicle.car.doors", "vehicle.bike", "vehicle.bike.gears")
                verifyResponseFields(Garage(Vehicle.Car(4))) { responseFieldsOf<Garage>() }
                verifyResponseFields(Vehicle.Bike(21)) { responseFieldsOf<Vehicle>() }
            }

            then("하위 타입 객체의 설명은 language 를 따른다") {
                val korean = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    responseFieldsOf<Garage>().byPath().getValue("vehicle.car").descriptor.description
                }

                responseFieldsOf<Garage>().byPath().getValue("vehicle.car").descriptor.description shouldBe
                    "Value when the type is car"
                korean shouldBe "car 타입일 때의 값"
            }
        }

        given("@JsonTypeInfo(include = EXISTING_PROPERTY) sealed 타입") {
            then("판별자 프로퍼티에 하위 타입 이름 목록과 설명이 붙는다") {
                val type = responseFieldsOf<Event>().byPath().getValue("type").descriptor

                type.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe listOf("CREATED", "DELETED")
                type.description.toString() shouldStartWith "Type discriminator"
            }

            then("language 가 ko 면 판별자 설명이 한국어다") {
                val type = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    responseFieldsOf<Event>().byPath().getValue("type").descriptor
                }

                type.description.toString() shouldStartWith "타입 구분자"
            }
        }

        given("@JsonFormat(shape)") {
            then("NUMBER·STRING 은 필드 타입을 바꾸고 실제 직렬화와 맞는다") {
                val fields = responseFieldsOf<Formatted>().byPath()
                // Spring 의 매퍼는 날짜를 문자열로 쓴다 (WRITE_DATES_AS_TIMESTAMPS 끔). shape = NUMBER 면 숫자다
                val mapper = Jackson2ObjectMapperBuilder.json().build<ObjectMapper>()

                fields.getValue("at").descriptor.type shouldBe JsonFieldType.NUMBER
                fields.getValue("price").descriptor.type shouldBe JsonFieldType.STRING
                verifyResponseFields(Formatted(Date(0), BigDecimal("1.50")), mapper) {
                    responseFieldsOf<Formatted>()
                }
            }
        }

        given("@JsonFormat(shape = NUMBER) Enum") {
            then("순번 숫자라 문자열 값 목록을 붙이지 않고 실제 직렬화와 맞는다") {
                val level = responseFieldsOf<Ordinal>().single().descriptor

                level.type shouldBe JsonFieldType.NUMBER
                level.attributes[ENUM_VALUES_ATTRIBUTE] shouldBe null
                verifyResponseFields(Ordinal(EventType.DELETED)) { responseFieldsOf<Ordinal>() }
            }
        }

        given("@JsonAnyGetter 가 있는 클래스") {
            then("맵 프로퍼티는 빼고, 다른 객체 안에 있으면 subsection 이라 펼쳐진 키도 통과한다") {
                val fields = responseFieldsOf<Product>().byPath()

                fields.keys.toList() shouldContainExactly listOf("attributes", "attributes.id")
                fields.getValue("attributes").descriptor.shouldBeInstanceOf<SubsectionDescriptor>()
                verifyResponseFields(Product(Attributes(1))) { responseFieldsOf<Product>() }
            }
        }

        given("Spring ProblemDetail") {
            val mapper = Jackson2ObjectMapperBuilder.json().build<ObjectMapper>()

            then("RFC 9457 필드만 optional 로 추출하고 properties 맵은 필드가 아니다") {
                val fields = responseFieldsOf<ProblemDetail>().byPath()

                fields.keys.toList() shouldContainExactly listOf("type", "title", "status", "detail", "instance")
                fields.values.all { it.descriptor.isOptional } shouldBe true
                fields.getValue("status").descriptor.description shouldBe "HTTP status code"
                verifyResponseFields(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "없음"), mapper) {
                    responseFieldsOf<ProblemDetail>()
                }
            }

            then("language 가 ko 면 필드 설명이 한국어다") {
                val fields = RestDocsConfiguration.withSettings({ language = DocsLanguage.KO }) {
                    responseFieldsOf<ProblemDetail>().byPath()
                }

                fields.getValue("status").descriptor.description shouldBe "HTTP 상태 코드"
                fields.getValue("title").descriptor.description shouldBe "문제 유형의 짧은 요약"
            }

            then("다른 객체 안에서는 확장 항목이 있어도 통과한다") {
                val problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply { setProperty("code", "E1") }

                verifyResponseFields(Problems(problem), mapper) { responseFieldsOf<Problems>() }
            }
        }
    })
