package services.orderemail

import kotlinx.serialization.json.*

/** Only the agreed order-confirmation and commercial-invoice templates may be used. */
class OrderConfirmationPayload(private val emails: SendOrderEmailService) {
    fun prepare(order: JsonObject): JsonObject {
        val snapshot = order.getValue("snapshot").jsonObject
        val data = snapshot["emailDocumentData"] as? JsonObject
            ?: throw EmailPayloadValidationException(listOf("emailDocumentData (approved invoice data required)"))
        val context = snapshot["emailContext"] as? JsonObject ?: JsonObject(emptyMap())
        return emails.prepare(order.getValue("id").jsonPrimitive.content, OrderEmailTemplate.ORDER_CONFIRMATION,
            context, OrderDocumentDataParser.parse(data))
    }
}
