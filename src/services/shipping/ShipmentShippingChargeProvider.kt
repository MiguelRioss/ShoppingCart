package services.shipping

import domain.cart.ShoppingCart
import domain.checkout.CheckoutShippingCharge
import productdatabaseaccesslayer.ProductDataAccess
import shipment.core.ShipmentAddress
import shipment.core.ShipmentProviderService
import services.common.ServiceErrorCode
import services.common.ServiceException

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
                products = shipmentRequest.products
            )

        val cheapest =
            quotes
                .filter {
                    it.currency.equals("EUR", ignoreCase = true)
                }
                .minByOrNull {
                it.amount
            }
                ?: throw ServiceException(
                    errorCode = ServiceErrorCode.ShippingQuoteCurrencyUnavailable
                )

        return cheapest.toCheckoutShippingCharge()
    }
}
