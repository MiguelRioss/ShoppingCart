package dto.checkout

import domain.checkout.CheckoutLineItem

data class PaymentCheckoutRequest(
    val cartId: String,
    val sessionId: String,
    val successUrl: String,
    val cancelUrl: String,
    val customerEmail: String? = null,
    val lineItems: List<CheckoutLineItem>
)