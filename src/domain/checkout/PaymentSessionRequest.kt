package domain.payment

import domain.checkout.CheckoutLineItem

data class PaymentSessionRequest(
    val cartId: String,
    val sessionId: String,
    val successUrl: String,
    val cancelUrl: String,
    val customerEmail: String? = null,
    val lineItems: List<CheckoutLineItem>
)