package services

interface CheckoutService {
    fun createCheckout(
        sessionId: String,
        successUrl: String,
        cancelUrl: String,
        customerEmail: String? = null
    ): CheckoutSession
}
