package domain.checkout.payment

import domain.checkout.CheckoutLineItem

/**
 * Payment-provider command for creating a checkout session.
 *
 * @property cartId shopping cart id used for provider metadata
 * @property sessionId application session id used for provider metadata
 * @property successUrl URL where the customer returns after successful payment
 * @property cancelUrl URL where the customer returns after canceling payment
 * @property customerEmail optional email address passed to the provider
 * @property lineItems product and optional shipping line items to charge
 */
data class CheckoutPaymentRequest(
    val cartId: String,
    val sessionId: String,
    val successUrl: String,
    val cancelUrl: String,
    val customerEmail: String? = null,
    val lineItems: List<CheckoutLineItem>
)
