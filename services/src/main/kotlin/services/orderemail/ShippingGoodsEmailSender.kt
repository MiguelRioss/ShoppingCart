package services.orderemail

import java.time.LocalDate
import kotlinx.serialization.json.*

/** Sends supplier packing and delivery emails when the fulfilment system triggers them. */
class ShippingGoodsEmailSender(
    private val mapper: PaidOrderEmailPayloadMapper,
    private val api: EmailSenderApi
) {
    fun sendPackingList(orderId: String, shippingDate: LocalDate): JsonObject =
        send(mapper.fromPaidOrder(orderId, setOf("packing-list"), shippingDate), "packing-list")

    fun sendDeliveryNote(orderId: String, shippingDate: LocalDate): JsonObject =
        send(mapper.fromPaidOrder(orderId, setOf("delivery-note"), shippingDate), "delivery-note")

    /** Retries are safe: EMAIL_SENDER claims each email using its order-specific idempotency key. */
    fun sendBoth(orderId: String, shippingDate: LocalDate): List<JsonObject> {
        val payload = mapper.fromPaidOrder(orderId, setOf("packing-list", "delivery-note"), shippingDate)
        return listOf(send(payload, "packing-list"), send(payload, "delivery-note"))
    }

    private fun send(input: JsonObject, documentType: String): JsonObject {
        val order = input.getValue("order").jsonObject
        val document = input.getValue("documents").jsonObject.getValue(documentType).jsonObject
        val fulfilment = input.getValue("fulfilment").jsonObject.getValue(documentType).jsonObject
        val supplierEmail = fulfilment["supplier_email"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing fulfilment.$documentType.supplier_email")
        val templateId = "supplier-$documentType"
        val baseEventId = input.getValue("eventId").jsonPrimitive.content
        val baseKey = input.getValue("idempotencyKey").jsonPrimitive.content
        val variables = JsonObject(fulfilment - "supplier_email" + mapOf(
            "order_reference" to JsonPrimitive(order.getValue("id").jsonPrimitive.content)
        ))
        val payload = buildJsonObject {
            put("templateId", templateId)
            put("eventId", "$baseEventId:$templateId")
            put("idempotencyKey", "$baseKey:$templateId")
            put("orderId", order.getValue("id"))
            put("checkoutSessionId", order.getValue("checkoutSessionId"))
            put("to", buildJsonObject { put("email", supplierEmail) })
            put("variables", variables)
            put("attachments", buildJsonArray {
                add(buildJsonObject {
                    put("type", documentType)
                    put("filename", document.getValue("filename"))
                    put("contentType", document.getValue("contentType"))
                    put("contentBase64", document.getValue("contentBase64"))
                })
            })
        }
        return JsonObject(api.sendEmail(payload) + ("templateId" to JsonPrimitive(templateId)))
    }
}
