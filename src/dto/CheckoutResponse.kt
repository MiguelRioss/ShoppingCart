package dto

import services.CheckoutSession
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class CheckoutResponse(
    val checkoutSession: CheckoutSession
) {
    fun toJson(): String =
        buildJsonObject {
            put("id", JsonPrimitive(checkoutSession.id))
            put("provider", JsonPrimitive(checkoutSession.provider))
            put("status", JsonPrimitive(checkoutSession.status))
            checkoutSession.url?.let { put("url", JsonPrimitive(it)) }
        }.toString()
}
