package services.orderemail

import db.OrderRepository
import kotlinx.serialization.json.*

class PaymentEmailNeedsReviewException : RuntimeException("Payment email submission needs review; delivery outcome may be uncertain")

/** Called only after a verified paid webhook has persisted the order. */
class AutomaticPaymentEmailService(
    private val orders: OrderRepository,
    private val submissions: PaymentEmailSubmissionStore,
    private val prepare: (JsonObject) -> JsonObject,
    private val sender: EmailSenderClient
) {
    fun onPaid(checkoutId: String) {
        when (submissions.state(checkoutId)) {
            "SUBMITTED" -> return
            "UNCERTAIN", "SUBMITTING" -> throw PaymentEmailNeedsReviewException()
        }
        val order = orders.findByCheckoutForEmail(checkoutId) ?: throw OrderEmailNotFoundException()
        if (order["status"]?.jsonPrimitive?.content != "PAID") return
        // Validation/generation happens before the claim: missing data can be corrected and retried.
        val payload = prepare(order)
        if (!submissions.claim(checkoutId)) {
            if (submissions.state(checkoutId) == "SUBMITTED") return
            throw PaymentEmailNeedsReviewException()
        }
        try {
            sender.send(payload)
            submissions.complete(checkoutId)
        } catch (error: EmailSenderConfigurationException) {
            submissions.release(checkoutId) // No network attempt was made; a later Stripe retry is safe.
            throw error
        } catch (error: Exception) {
            // An SMTP/network failure may occur after acceptance. Do not resend automatically.
            submissions.uncertain(checkoutId)
            throw PaymentEmailNeedsReviewException()
        }
    }
}
