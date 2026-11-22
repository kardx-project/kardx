package com.example.restdocs.shipping

import io.github.kardx.annotation.Description

data class ShippingFeeResponse(@Description("배송 지역") val region: String, @Description("배송비 (원)") val fee: Long)
