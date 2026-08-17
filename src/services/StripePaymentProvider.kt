package services

import config.Environment

class StripePaymentProvider(
    private val checkoutApiUrl: String? = Environment.get("STRIPE_CHECKOUT_API_URL")
) : PaymentProvider {
    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        if (checkoutApiUrl.isNullOrBlank()) {
            return CheckoutSession(
                id = "stripe_pending_${request.cartId}",
                provider = "stripe",
                status = "api_link_missing",
                url = null
            )
        }

        throw ServiceException(
            errorCode = ServiceErrorCode.CheckoutCreationFailed,
            description = "Stripe checkout API link is configured but the HTTP call is not implemented yet"
        )
    }
}
