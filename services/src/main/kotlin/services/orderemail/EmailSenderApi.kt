package services.orderemail

import kotlinx.serialization.json.JsonObject

/** Transport boundary for the authenticated Email Sender API. */
interface EmailSenderApi {
    /** Starts the paid-order journey: customer confirmation plus supplier purchase order. */
    fun startPurchaseComplete(payload: JsonObject): JsonObject

    /** Sends one named email, used for fulfilment documents triggered after checkout. */
    fun sendEmail(payload: JsonObject): JsonObject
}
