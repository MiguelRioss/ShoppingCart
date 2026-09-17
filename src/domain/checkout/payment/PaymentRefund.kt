package domain.checkout.payment

import java.math.BigDecimal

data class PaymentRefund(
    val id: String,
    val paymentId: String,
    val amount: BigDecimal,
    val currency: String,
    val status: PaymentRefundStatus
)

enum class PaymentRefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED
}