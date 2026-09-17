package services.checkout.payment.core

import domain.checkout.payment.CheckoutPaymentRequest
import domain.checkout.CheckoutSession
import domain.checkout.payment.PaymentEvent
import domain.checkout.payment.PaymentRefund
import domain.checkout.payment.PaymentWebhookRequest
import domain.checkout.payment.RefundPaymentRequest

/**
 * Boundary implemented by payment provider adapters.
 *
 * Checkout business logic depends on this interface rather than directly on
 * Stripe or any other provider, which keeps payment integration replaceable.
 */
interface PaymentProviderInterface {

    /**
     * Payment provider represented by this adapter.
     */
    val type: PaymentProviderType

    /**
     * Creates a hosted checkout session for the supplied cart and line items.
     */
    fun createCheckoutSession(
        request: CheckoutPaymentRequest
    ): CheckoutSession

    /**
     * Loads the latest state of an existing checkout session.
     */
    fun getCheckoutSession(
        checkoutSessionId: String
    ): CheckoutSession

    /**
     * Captures a payment when the provider supports delayed capture.
     */
    fun capturePayment(
        paymentId: String
    ): CheckoutSession

    /**
     * Refunds all or part of a captured payment.
     */
    fun refundPayment(
        request: RefundPaymentRequest
    ): PaymentRefund

    /**
     * Converts a provider webhook request into a normalized payment event.
     */
    fun parseWebhook(
        request: PaymentWebhookRequest
    ): PaymentEvent
}
