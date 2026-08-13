package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Request body accepted by POST /cart.
 */
data class SaveShoppingCartRequest(
    val sessionId: String?,
    val products: List<SaveShoppingCartProductRequest>
) {
    val isValid: Boolean
        get() = !sessionId.isNullOrBlank() &&
            products.isNotEmpty() &&
            products.all { it.isValid }

    companion object {
        /**
         * Parses a raw JSON request body into a save-cart request.
         */
        fun fromJson(requestBody: String, json: Json = Json): SaveShoppingCartRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject

            return SaveShoppingCartRequest(
                sessionId = body.stringValue("sessionId"),
                products = body.productsValue()
            )
        }

        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.jsonPrimitive?.content

        private fun JsonObject.productsValue(): List<SaveShoppingCartProductRequest> =
            this["products"]?.let { products ->
                runCatching {
                    products.jsonArray.map { it.toProductRequest() }
                }.getOrDefault(emptyList())
            } ?: emptyList()

        private fun JsonElement.toProductRequest(): SaveShoppingCartProductRequest {
            val body = jsonObject

            return SaveShoppingCartProductRequest(
                productId = body["productId"]?.jsonPrimitive?.longOrNull,
                quantityM2 = body["quantityM2"]?.jsonPrimitive?.doubleOrNull
            )
        }
    }
}

/**
 * Product line requested for a saved cart.
 */
data class SaveShoppingCartProductRequest(
    val productId: Long?,
    val quantityM2: Double?
) {
    val isValid: Boolean
        get() = productId != null && quantityM2 != null && quantityM2 > 0.0
}
