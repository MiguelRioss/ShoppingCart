package services

import java.math.BigDecimal

interface PaymentProvider {
    fun createCheckout(request: CheckoutRequest): CheckoutSession
}

data class CheckoutRequest(
    val cartId: String,
    val sessionId: String,
    val successUrl: String,
    val cancelUrl: String,
    val customerEmail: String?,
    val lineItems: List<CheckoutLineItem>
)

data class CheckoutLineItem(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val amountTotal: BigDecimal,
    val currency: String = "eur"
)

data class CheckoutSession(
    val id: String,
    val provider: String,
    val status: String,
    val url: String?
)
