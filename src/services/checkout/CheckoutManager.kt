package services.checkout

import domain.checkout.Checkout
import domain.checkout.CheckoutSession
import domain.payment.PaymentSessionRequest
import java.math.BigDecimal
import productdatabaseaccesslayer.ProductDataAccess
import services.cart.ShoppingCartService
import services.common.ServiceErrorCode
import services.common.ServiceException
import services.payment.PaymentProvider

class CheckoutManager(
    private val shoppingCartService: ShoppingCartService,
    private val paymentProvider: PaymentProvider,
    private val productDataAccess: ProductDataAccess? = null
) : CheckoutService {

    private val checkoutProductReader =
        CheckoutProductReader(
            productDataAccess
        )

    override fun createCheckout(
        checkout: Checkout
    ): CheckoutSession {
        checkout.validate()

        val cart =
            shoppingCartService.getCartBySessionId(
                checkout.sessionId
            )
                ?: throw ServiceException(
                    errorCode = ServiceErrorCode.CartNotFound,
                    description =
                        "No shopping cart exists for sessionId ${checkout.sessionId}"
                )

        if (cart.products.isEmpty()) {
            throw ServiceException(
                ServiceErrorCode.CartIsEmpty
            )
        }

        cart.products.forEach { product ->

            if (!product.squareMeters.isMultipleOfHalfSquareMeter()) {

                throw ServiceException(
                    errorCode =
                        ServiceErrorCode.CartQuantityNotHalfMeterMultiple,
                    description =
                        "Product ${product.productId} has " +
                                "${product.squareMeters} m2, " +
                                "but checkout quantities must be multiples of 0.5 m2"
                )
            }
        }

        val productLineItems =
            cart.products.map { product ->

                val productData =
                    checkoutProductReader.read(product)

                product.toCheckoutLineItem(
                    productData
                )
            }

        val shippingLineItem =
            checkout.shippingCharge
                ?.toCheckoutLineItem()

        val paymentSessionRequest =
            PaymentSessionRequest(
                cartId = cart.id.toString(),
                sessionId = checkout.sessionId,
                successUrl = checkout.successUrl,
                cancelUrl = checkout.cancelUrl,
                customerEmail = checkout.customerEmail,
                lineItems =
                    productLineItems +
                            listOfNotNull(
                                shippingLineItem
                            )
            )

        return paymentProvider.createPaymentSession(
            paymentSessionRequest
        )
    }

    private fun Checkout.validate() {
        if (sessionId.isBlank()) {
            throw invalidCheckout("sessionId is required")
        }

        if (successUrl.isBlank()) {
            throw invalidCheckout("successUrl is required")
        }

        if (cancelUrl.isBlank()) {
            throw invalidCheckout("cancelUrl is required")
        }
    }

    private fun invalidCheckout(description: String): ServiceException =
        ServiceException(
            errorCode = ServiceErrorCode.CheckoutRequestInvalid,
            description = description
        )

    private fun Double.isMultipleOfHalfSquareMeter(): Boolean =
        BigDecimal.valueOf(this)
            .remainder(BigDecimal("0.5"))
            .compareTo(BigDecimal.ZERO) == 0
}
