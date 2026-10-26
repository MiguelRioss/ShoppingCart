package services.checkout.core

import domain.checkout.payment.CheckoutPaymentRequest
import domain.checkout.CheckoutSession
import domain.checkout.toCheckoutLineItem
import domain.checkout.CreateCheckoutSessionRequest
import domain.checkout.toShipmentDestination
import services.cart.PurchasableProductReader
import services.cart.ShoppingCartService
import services.checkout.payment.core.PaymentProviderInterface
import services.common.ServiceErrorCode
import services.common.ServiceException
import services.shipping.ShippingChargeProvider

/**
 * Creates checkout sessions for saved shopping carts.
 *
 * The manager resolves cart products into payment line items and appends an
 * optional carrier-neutral shipping line item when the caller supplies one.
 */
class CheckoutManager(
    private val shoppingCartService: ShoppingCartService,
    private val paymentProvider: PaymentProviderInterface,
    private val shippingChargeProvider: ShippingChargeProvider? = null,
    purchasableProductReader: PurchasableProductReader,
    private val orderRepository: db.OrderRepository = db.offline.InMemoryOrderRepository()
) : CheckoutService {

    private val validator =
        CheckoutValidator()

    private val checkoutProductResolver =
        CheckoutProductResolver(
            purchasableProductReader
        )

    override fun createCheckoutSession(
        request: CreateCheckoutSessionRequest
    ): CheckoutSession {
        val cart =
            shoppingCartService.getCartBySessionId(
                request.sessionId
            )
                ?: throw ServiceException(
                    errorCode =
                        ServiceErrorCode.CartNotFound,
                    description =
                        "No shopping cart exists for sessionId ${request.sessionId}"
                )

        require(cart.userId == null || cart.userId == request.userId) {
            "This shopping cart belongs to another user"
        }

        validator.validateCart(
            cart
        )
        val productLineItems =
            cart.products.map(
                checkoutProductResolver::resolve
            )

        require(
            request.shippingCharge != null ||
                    request.deliveryAddress != null
        ) {
            "deliveryAddress is required when shippingCharge is not supplied"
        }

        val shippingCharge =
            request.shippingCharge
                ?: requireNotNull(shippingChargeProvider) {
                    "shippingChargeProvider is required when shippingCharge is not supplied"
                }.calculateShipping(
                    cart = cart,
                    destination = request.toShipmentDestination()
                )

        val shippingLineItem =
            shippingCharge?.toCheckoutLineItem()
        val lineItems =
            productLineItems +
                    listOfNotNull(
                        shippingLineItem
                    )


        val paymentRequest =
            CheckoutPaymentRequest(
                cartId = cart.id.toString(),
                sessionId = request.sessionId,
                successUrl = request.successUrl,
                cancelUrl = request.cancelUrl,
                customerEmail = request.customerEmail,
                lineItems = lineItems
            )

        val checkout = paymentProvider.createCheckoutSession(
            paymentRequest
        )
        orderRepository.create(checkout.id, request.userId, orderSnapshot(request, cart, lineItems))
        return checkout
    }


}
