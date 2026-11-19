package domain.checkout.payment

import java.math.BigDecimal

/** Result of a full or partial refund, expressed in the major [currency] unit. */
data class PaymentRefund(
    val id: String,
    val paymentId: String,
    val amount: BigDecimal,
    val currency: String,
    val status: PaymentRefundStatus
)

/** Normalized processing states for provider refunds. */
enum class PaymentRefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}
