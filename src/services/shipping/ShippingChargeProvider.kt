package services.shipping

import domain.cart.ShoppingCart
import domain.checkout.CheckoutShippingCharge
import shipment.core.ShipmentAddress

interface ShippingChargeProvider {

    fun calculateShipping(
        cart: ShoppingCart,
        destination: ShipmentAddress
    ): CheckoutShippingCharge?
}