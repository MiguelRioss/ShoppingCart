package dto.cart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import services.cart.CartProductInput
import stringValueIncludingBlank

/**
 * HTTP request body for `POST /cart`.
 *
 * @property sessionId browser or application session id that owns the cart
 * @property products product quantities to store; saving replaces the previous list
 */
data class SaveShoppingCartRequest(
    val sessionId: String?,
    val products: List<SaveShoppingCartProductRequest>
)

/**
 * Product quantity entry in a cart save request.
 *
 * @property productId catalog product id
 * @property quantityM2 requested quantity in square meters
 */
data class SaveShoppingCartProductRequest(
    val productId: Long?,
    val quantityM2: Double?,
    val isSample: Boolean?
)

/**
 * Converts a validated request product into the pair used by the cart service.
 */
fun SaveShoppingCartProductRequest.toEntity(): CartProductInput =
    CartProductInput(
        productId = requireNotNull(productId),
        quantityM2 = quantityM2,
        isSample = isSample ?: false
    )


/**
 * Parses the raw JSON body for `POST /cart`.
 */
fun parseSaveShoppingCartRequest(requestBody: String): SaveShoppingCartRequest {
    val body = Json.parseToJsonElement(requestBody).jsonObject

    return SaveShoppingCartRequest(
        sessionId = body.stringValueIncludingBlank("sessionId"),
        products = body["products"]?.let { products ->
            runCatching {
                products.jsonArray.map { it.toSaveShoppingCartProductRequest() }
            }.getOrDefault(emptyList())
        } ?: emptyList()
    )
}
private fun JsonElement.toSaveShoppingCartProductRequest(): SaveShoppingCartProductRequest {
    val body = jsonObject

    return SaveShoppingCartProductRequest(
        productId = body["productId"]?.jsonPrimitive?.longOrNull,
        quantityM2 = body["quantityM2"]?.jsonPrimitive?.doubleOrNull,
        isSample = body["isSample"]?.jsonPrimitive?.booleanOrNull
    )
}
