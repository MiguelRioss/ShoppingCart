package domain.checkout.payment

import java.math.BigDecimal

/** Refund command; a null [amount] requests a full payment refund. */
data class RefundPaymentRequest(
    val paymentId: String,
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val reason: String? = null
)
