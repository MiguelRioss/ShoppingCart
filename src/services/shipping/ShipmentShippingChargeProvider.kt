package services.shipping

import domain.cart.ShoppingCart
import domain.checkout.CheckoutShippingCharge
import productdatabaseaccesslayer.ProductDataAccess
import domain.shipment.ShipmentAddress
import domain.shipment.ShipmentRateRequest
import shipment.core.ShippingProvider
import services.common.ServiceErrorCode
import services.common.ServiceException
import java.math.BigDecimal

/**
 * Calculates checkout shipping through the carrier-neutral middleware.
 *
 * One rate request is made per supplier origin. Only EUR quotes are eligible,
 * the cheapest quote is selected for each origin, and those selected charges
 * are combined into the single shipping amount paid during checkout.
 */
class ShipmentShippingChargeProvider(
    private val shipmentProvider: ShippingProvider,
    private val productDataAccess: ProductDataAccess
) : ShippingChargeProvider {

    private val shipmentShippingRequestMapper =
        ProductShipmentRequestMapper(
            productDataAccess
        )

    /**
     * Maps cart lines to origin groups and requests rates for each group.
     * @throws ServiceException when any origin has no EUR quote
     */
    override fun calculateShipping(
        cart: ShoppingCart,
        destination: ShipmentAddress
    ): CheckoutShippingCharge? {

        val shipmentRequests =
            shipmentShippingRequestMapper.toShipmentShippingRequests(
                cart
            )

        val selectedQuotes =
            shipmentRequests.map { shipmentRequest ->
                shipmentProvider.getQuotes(
                    ShipmentRateRequest(
                        from = shipmentRequest.from,
                        to = destination,
                        packages = shipmentRequest.products,
                        preferredCurrency = "EUR"
                    )
                )
                    .filter {
                        it.currency.equals("EUR", ignoreCase = true)
                    }
                    .minByOrNull {
                        it.amount
                    }
                    ?: throw ServiceException(
                        errorCode = ServiceErrorCode.ShippingQuoteCurrencyUnavailable,
                        description =
                            "No EUR shipping quote is available from " +
                                "${shipmentRequest.from.city}, ${shipmentRequest.from.countryCode}"
                    )
            }

        if (selectedQuotes.size == 1) {
            return selectedQuotes.single().toCheckoutShippingCharge()
        }

        val total =
            selectedQuotes.fold(BigDecimal.ZERO) { amount, quote ->
                amount + BigDecimal.valueOf(quote.amount)
            }

        return CheckoutShippingCharge(
            serviceType = "MULTI_ORIGIN",
            serviceName = "Shipping (${selectedQuotes.size} origins)",
            amountTotal = total,
            currency = "eur",
            estimatedDeliveryDate =
                selectedQuotes.mapNotNull { it.estimatedDeliveryDate }.maxOrNull(),
            originalAmount = total,
            originalCurrency = "EUR"
        )
    }
}
