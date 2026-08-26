package services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess

class DefaultCheckoutService(
    private val shoppingCartService: ShoppingCartService,
    private val paymentProvider: PaymentProvider,
    private val productDataAccess: ProductDataAccess? = null,
    private val json: Json = Json
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
                    val checkoutProduct = product.checkoutProduct()

                    CheckoutLineItem(
                        productId = product.productId,
                        name = checkoutProduct.name,
                        quantity = product.amountBoxes,
                        amountTotal = product.totalPricePerProduct,
                        imageUrl = checkoutProduct.imageUrl
                    )
                }
            )
        )
    }

    private fun domain.ShoppingCartProduct.checkoutProduct(): CheckoutProduct {
        val fallback = CheckoutProduct(name = "Product $productId")
        val productJson = productDataAccess?.getProductById(productId) ?: return fallback

        return runCatching {
            val product = json.parseToJsonElement(productJson).jsonObject
            CheckoutProduct(
                name = product["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: fallback.name,
                imageUrl = product["image"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            )
        }.getOrDefault(fallback)
    }

    private data class CheckoutProduct(
        val name: String,
        val imageUrl: String? = null
    )
}
