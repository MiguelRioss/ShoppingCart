package dto.cart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import stringValueIncludingBlank

/** HTTP request body for clearing the cart associated with [sessionId]. */
data class ClearShoppingCartRequest(
    val sessionId: String?
)

/** Parses the JSON body accepted by `POST /cart/clear`. */
fun parseClearShoppingCartRequest(requestBody: String): ClearShoppingCartRequest {
    val body = Json.parseToJsonElement(requestBody).jsonObject

    return ClearShoppingCartRequest(
        sessionId = body.stringValueIncludingBlank("sessionId")
    )
}
