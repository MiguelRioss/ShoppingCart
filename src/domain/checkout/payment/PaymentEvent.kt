package domain.checkout.payment

data class PaymentEvent(
    val id: String,
    val type: PaymentEventType,
    val paymentId: String? = null,
    val checkoutSessionId: String? = null
)

enum class PaymentEventType {
    CHECKOUT_COMPLETED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    REFUND_SUCCEEDED,
    REFUND_FAILED,
    UNKNOWN
}