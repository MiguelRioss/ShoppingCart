package dto.checkout

import domain.checkout.CreateCheckoutSessionRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Checkout input. Delivery address and email come from the authenticated account. */
data class CreateCheckoutRequest(
    val sessionId: String?,
    val successUrl: String?,
    val cancelUrl: String?
)

fun parseCreateCheckoutRequest(requestBody: String, json: Json = Json): CreateCheckoutRequest {
    val body = json.parseToJsonElement(requestBody).jsonObject
    require("deliveryAddress" !in body) {
        "deliveryAddress is obsolete for checkout; update the delivery address on your account"
    }
    require("shippingCharge" !in body) {
        "shippingCharge is calculated by the server and cannot be supplied"
    }
    return CreateCheckoutRequest(
        sessionId = body.stringValueIncludingBlank("sessionId"),
        successUrl = body.stringValueIncludingBlank("successUrl"),
        cancelUrl = body.stringValueIncludingBlank("cancelUrl")
    )
}

fun CreateCheckoutRequest.toDomain(): CreateCheckoutSessionRequest =
    CreateCheckoutSessionRequest(
        sessionId = sessionId.required("sessionId"),
        successUrl = successUrl.required("successUrl"),
        cancelUrl = cancelUrl.required("cancelUrl"),
        customerEmail = null,
        shippingCharge = null
    )

private fun String?.required(fieldName: String): String {
    require(!isNullOrBlank()) { "$fieldName is required" }
    return this
}

private fun JsonObject.stringValueIncludingBlank(name: String): String? =
    this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
