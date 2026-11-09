package services.order

import domain.cart.ShoppingCart
import domain.checkout.CheckoutLineItem
import domain.checkout.CheckoutShippingCharge
import domain.checkout.CreateCheckoutSessionRequest

interface OrderService {
    fun createForCheckout(
        checkoutId: String,
        request: CreateCheckoutSessionRequest,
        cart: ShoppingCart,
        productLineItems: List<CheckoutLineItem>,
        shippingCharge: CheckoutShippingCharge?
    )
}
