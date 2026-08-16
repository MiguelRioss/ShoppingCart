package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ClearShoppingCartRequest(
    val sessionId: String?
) {
    val isValid: Boolean
        get() = !sessionId.isNullOrBlank()

    companion object {
        fun fromJson(requestBody: String, json: Json = Json): ClearShoppingCartRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject

            return ClearShoppingCartRequest(
                sessionId = body["sessionId"]?.jsonPrimitive?.content
            )
        }
    }
}
