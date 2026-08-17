package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CreateCheckoutRequest(
    val sessionId: String?,
    val successUrl: String?,
    val cancelUrl: String?,
    val customerEmail: String?
) {
    val isValid: Boolean
        get() = !sessionId.isNullOrBlank() &&
            !successUrl.isNullOrBlank() &&
            !cancelUrl.isNullOrBlank()

    companion object {
        fun fromJson(requestBody: String, json: Json = Json): CreateCheckoutRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject

            return CreateCheckoutRequest(
                sessionId = body["sessionId"]?.jsonPrimitive?.content,
                successUrl = body["successUrl"]?.jsonPrimitive?.content,
                cancelUrl = body["cancelUrl"]?.jsonPrimitive?.content,
                customerEmail = body["customerEmail"]?.jsonPrimitive?.content
            )
        }
    }
}
