package domain.checkout

data class Checkout(
    val sessionId: String,
    val successUrl: String,
    val cancelUrl: String,
    val customerEmail: String?,
    val destination: CheckoutDestination?,
    val shippingCharge: CheckoutShippingCharge?
)