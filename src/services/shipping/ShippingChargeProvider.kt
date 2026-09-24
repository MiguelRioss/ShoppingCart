package services.shipping

import domain.cart.ShoppingCart
import domain.checkout.CheckoutShippingCharge
import domain.shipment.ShipmentAddress

/** Boundary for calculating a checkout shipping charge from a saved cart. */
interface ShippingChargeProvider {

    /** Returns the selected carrier charge for delivering [cart] to [destination]. */
    fun calculateShipping(
        cart: ShoppingCart,
        destination: ShipmentAddress
    ): CheckoutShippingCharge?
}
