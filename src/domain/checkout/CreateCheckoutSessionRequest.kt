package domain.checkout

/**
 * Domain command for creating a checkout session from an existing shopping cart.
 *
 * The cart is identified by [sessionId]. Product line items are loaded from the
 * saved cart; [shippingCharge] is optional and lets another application provide
 * its own shipping calculation without coupling this project to a carrier API.
 *
 * @property sessionId browser or application session id associated with the cart
 * @property successUrl URL where the payment provider redirects after success
 * @property cancelUrl URL where the payment provider redirects after cancellation
 * @property customerEmail optional email passed to the payment provider
 * @property shippingCharge optional pre-calculated shipping charge to add to checkout
 * @property deliveryAddress destination address used when shipping is calculated by this service
 */
data class CreateCheckoutSessionRequest(
    val sessionId: String,
    val successUrl: String,
    val cancelUrl: String,
    val customerEmail: String?,
    val shippingCharge: CheckoutShippingCharge?,
    val deliveryAddress: CheckoutDeliveryAddress? = null
)
