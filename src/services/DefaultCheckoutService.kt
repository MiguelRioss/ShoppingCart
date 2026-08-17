package services

class DefaultCheckoutService(
    private val shoppingCartService: ShoppingCartService,
    private val paymentProvider: PaymentProvider
) : CheckoutService {
    override fun createCheckout(
        sessionId: String,
        successUrl: String,
        cancelUrl: String,
        customerEmail: String?
    ): CheckoutSession {
        val cart = shoppingCartService.getCartBySessionId(sessionId)
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.CartNotFound,
                description = "No shopping cart exists for sessionId $sessionId"
            )

        if (cart.products.isEmpty()) {
            throw ServiceException(ServiceErrorCode.CartIsEmpty)
        }

        return paymentProvider.createCheckout(
            CheckoutRequest(
                cartId = cart.id.toString(),
                sessionId = sessionId,
                successUrl = successUrl,
                cancelUrl = cancelUrl,
                customerEmail = customerEmail,
                lineItems = cart.products.map { product ->
                    CheckoutLineItem(
                        productId = product.productId,
                        name = "Product ${product.productId}",
                        quantity = product.amountBoxes,
                        amountTotal = product.totalPricePerProduct
                    )
                }
            )
        )
    }
}
