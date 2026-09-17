package domain.checkout.payment

import java.math.BigDecimal

data class RefundPaymentRequest(
    val paymentId: String,
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val reason: String? = null
)