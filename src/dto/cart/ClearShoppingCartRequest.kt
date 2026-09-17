package dto.cart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import stringValueIncludingBlank

data class ClearShoppingCartRequest(
    val sessionId: String?
)

fun parseClearShoppingCartRequest(requestBody: String): ClearShoppingCartRequest {
    val body = Json.parseToJsonElement(requestBody).jsonObject

    return ClearShoppingCartRequest(
        sessionId = body.stringValueIncludingBlank("sessionId")
    )
}