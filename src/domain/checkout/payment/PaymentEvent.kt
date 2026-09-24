package domain.checkout.payment

/** Provider-neutral payment event produced from a verified provider webhook. */
data class PaymentEvent(
    val id: String,
    val type: PaymentEventType,
    val paymentId: String? = null,
    val checkoutSessionId: String? = null
)

/** Payment lifecycle events understood by the application. */
enum class PaymentEventType {
    CHECKOUT_COMPLETED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    REFUND_SUCCEEDED,
    REFUND_FAILED,
    UNKNOWN
}
