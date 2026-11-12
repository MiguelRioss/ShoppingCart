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
    private val productDataAccess: ProductDataAccess,
    private val dutyTaxEstimator: DutyTaxEstimator? = null
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

        val requiresDdpEstimate = destination.countryCode.uppercase() !in EU_COUNTRIES &&
            destination.countryCode.uppercase() !in setOf("GB", "UK")
        val selectedCharges =
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
                    .map { quote ->
                        val charge = quote.toCheckoutShippingCharge()
                        if (!requiresDdpEstimate) charge else {
                            val estimate = requireNotNull(dutyTaxEstimator) {
                                "DDP duty and tax estimator is required for exports outside the EU"
                            }.estimate(
                                shipmentRequest.from, destination, shipmentRequest.products,
                                charge.amountTotal, quote.serviceType
                            )
                            require(estimate >= BigDecimal.ZERO) { "Invalid DDP estimate" }
                            charge.copy(
                                serviceName = "${charge.serviceName ?: "Shipping"} (DDP estimate)",
                                amountTotal = charge.amountTotal + estimate,
                                originalAmount = charge.amountTotal + estimate
                            )
                        }
                    }
                    .minByOrNull {
                        it.amountTotal
                    }
                    ?: throw ServiceException(
                        errorCode = ServiceErrorCode.ShippingQuoteCurrencyUnavailable,
                        description =
                            "No EUR shipping quote is available from " +
                                "${shipmentRequest.from.city}, ${shipmentRequest.from.countryCode}"
                    )
            }

        if (selectedCharges.size == 1) {
            return selectedCharges.single()
        }

        val total =
            selectedCharges.fold(BigDecimal.ZERO) { amount, charge ->
                amount + charge.amountTotal
            }

        return CheckoutShippingCharge(
            serviceType = "MULTI_ORIGIN",
            serviceName = "Shipping (${selectedCharges.size} origins)" +
                if (requiresDdpEstimate) " (DDP estimate)" else "",
            amountTotal = total,
            currency = "eur",
            estimatedDeliveryDate =
                selectedCharges.mapNotNull { it.estimatedDeliveryDate }.maxOrNull(),
            originalAmount = total,
            originalCurrency = "EUR"
        )
    }

    companion object {
        private val EU_COUNTRIES = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
            "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK",
            "SI", "ES", "SE"
        )
    }
}
