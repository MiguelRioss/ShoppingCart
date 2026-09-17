package dto.checkout

import domain.checkout.CheckoutSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Public response returned by `POST /checkout`.
 *
 * @property id provider checkout session id
 * @property provider normalized provider name, such as `stripe`
 * @property status normalized session status, such as `created`
 * @property url hosted checkout URL, when available
 */
data class CheckoutResponse(
    val id: String,
    val provider: String,
    val status: String,
    val url: String?
)

/**
 * Serializes the checkout response as compact JSON.
 */
fun CheckoutResponse.toJson(): String =
    buildJsonObject {
        put("id", JsonPrimitive(id))
        put("provider", JsonPrimitive(provider.toString()))
        put("status", JsonPrimitive(status))
        url?.let { put("url", JsonPrimitive(it)) }
    }.toString()
