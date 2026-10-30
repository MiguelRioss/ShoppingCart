package services.orderemail

import db.OrderRepository
import kotlinx.serialization.json.*

/** Called only after a verified paid webhook has persisted the order. */
class AutomaticPaymentEmailService(
    private val orders: OrderRepository,
    private val prepare: (JsonObject) -> JsonObject,
    private val sender: EmailSenderClient
) {
    fun onPaid(checkoutId: String) {
        val order = orders.findByCheckoutForEmail(checkoutId) ?: throw OrderEmailNotFoundException()
        if (order["status"]?.jsonPrimitive?.content != "PAID") return
        // EMAIL_SENDER owns history and duplicate suppression using the stable payload key.
        val payload = prepare(order)
        sender.send(payload)
    }
}
