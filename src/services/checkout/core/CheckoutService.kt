package services.checkout.core

import domain.checkout.CheckoutSession
import domain.checkout.CreateCheckoutSessionRequest

/**
 * Checkout use cases exposed by the application layer.
 */
interface CheckoutService {

    /**
     * Creates a provider checkout session for the saved cart identified by the request.
     */
    fun createCheckoutSession(
        request: CreateCheckoutSessionRequest
    ): CheckoutSession
}
