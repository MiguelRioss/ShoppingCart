/**
 * Boundary for external payment providers such as Stripe.
 *
 * Receives a provider-neutral payment session request and returns
 * the payment session created by the external provider.
 */
package services.payment

import domain.checkout.CheckoutSession
import domain.payment.PaymentSessionRequest

interface PaymentProvider {

    fun createPaymentSession(
        request: PaymentSessionRequest
    ): CheckoutSession
}