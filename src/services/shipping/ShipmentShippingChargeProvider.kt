package services.shipping

import domain.cart.ShoppingCart
import domain.checkout.CheckoutShippingCharge
import productdatabaseaccesslayer.ProductDataAccess
import shipment.core.ShipmentAddress
import shipment.core.ShipmentProviderService

class ShipmentShippingChargeProvider(
    private val shipmentProvider: ShipmentProviderService,
    private val productDataAccess: ProductDataAccess
) : ShippingChargeProvider {

    private val shipmentShippingRequestMapper =
        ProductShipmentRequestMapper(
            productDataAccess
        )

    override fun calculateShipping(
        cart: ShoppingCart,
        destination: ShipmentAddress
    ): CheckoutShippingCharge? {

        val shipmentRequest =
            shipmentShippingRequestMapper.toShipmentShippingRequest(
                cart
            )

        val quotes =
            shipmentProvider.getQuotes(
                from = shipmentRequest.from,
                to = destination,
                product = shipmentRequest.product
            )

        val cheapest =
            quotes.minByOrNull {
                it.amount
            }
                ?: return null

        return cheapest.toCheckoutShippingCharge()
    }
}
