package services.shipping.fulfillment

import db.OrderRepository
import java.time.LocalDate
import kotlinx.serialization.json.*
import services.orderemail.ShippingGoodsEmailSender

/** Books a dated FedEx shipment, then creates and emails its two fulfilment PDFs. */
class ShippingGoodsPreparationService(
    private val orders: OrderRepository,
    private val booking: PaidOrderShipmentBookingService,
    private val emailSender: ShippingGoodsEmailSender
) {
    fun prepare(orderId: String, shippingDate: LocalDate): JsonObject {
        check(booking.bookPaidOrder(orderId, shippingDate)) {
            "FedEx did not confirm every shipment AWB; fulfilment emails were not sent"
        }
        val order = orders.findForEmail(orderId)
            ?: throw IllegalArgumentException("Order not found: $orderId")
        val checkoutId = order.text("checkoutSessionId")
            ?: throw IllegalArgumentException("Order checkout session is missing")
        val awbs = orders.shipmentBookings(checkoutId).mapNotNull { it as? JsonObject }.mapNotNull { entry ->
            val booking = entry["booking"] as? JsonObject ?: return@mapNotNull null
            if (entry.text("status") != "BOOKED" || booking.text("requestedShippingDate") != shippingDate.toString())
                return@mapNotNull null
            booking.text("masterTrackingNumber")
        }.distinct()
        check(awbs.isNotEmpty()) { "FedEx booking did not return an AWB" }
        val responses = emailSender.sendBoth(orderId, shippingDate)
        return buildJsonObject {
            put("orderId", orderId)
            put("shippingDate", shippingDate.toString())
            put("awb", awbs.joinToString(", "))
            put("emails", JsonArray(responses.map { response ->
                buildJsonObject {
                    response["templateId"]?.let { put("templateId", it) }
                    response["emailId"]?.let { put("emailId", it) }
                    response["messageId"]?.let { put("messageId", it) }
                    put("status", response["status"] ?: JsonPrimitive("accepted"))
                }
            }))
        }
    }

    private fun JsonObject.text(field: String): String? =
        (this[field] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}
