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
 * One rate request is made per supplier origin. Only EUR quotes are eligible;
 * the cheapest transport quote is selected, a 10% shipping buffer is added,
 * and selected charges are combined into the single checkout shipping amount.
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
                val cheapestQuote = shipmentProvider.getQuotes(
                    ShipmentRateRequest(
                        from = shipmentRequest.from,
                        to = destination,
                        packages = shipmentRequest.products,
                        preferredCurrency = "EUR"
                    )
                ).filter { it.currency.equals("EUR", ignoreCase = true) }
                    .minByOrNull { it.amount }
                    ?: throw ServiceException(
                        errorCode = ServiceErrorCode.ShippingQuoteCurrencyUnavailable,
                        description =
                            "No EUR shipping quote is available from " +
                                "${shipmentRequest.from.city}, ${shipmentRequest.from.countryCode}"
                    )
                val baseCharge = cheapestQuote.toCheckoutShippingCharge()
                val shippingWithBuffer = baseCharge.amountTotal
                    .multiply(SHIPPING_BUFFER_MULTIPLIER)
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                val chargeWithBuffer = baseCharge.copy(
                    amountTotal = shippingWithBuffer,
                    originalAmount = baseCharge.amountTotal
                )
                if (!requiresDdpEstimate) chargeWithBuffer else {
                    val estimate = requireNotNull(dutyTaxEstimator) {
                        "DDP duty and tax estimator is required for exports outside the EU"
                    }.estimate(
                        shipmentRequest.from, destination, shipmentRequest.products,
                        shippingWithBuffer, cheapestQuote.serviceType
                    )
                    require(estimate >= BigDecimal.ZERO) { "Invalid DDP estimate" }
                    chargeWithBuffer.copy(
                        serviceName = "${chargeWithBuffer.serviceName ?: "Shipping"} (DDP estimate)",
                        amountTotal = shippingWithBuffer + estimate
                    )
                }
            }

        if (selectedCharges.size == 1) {
            return selectedCharges.single().copy(
                originServiceTypes = listOfNotNull(selectedCharges.single().serviceType)
            )
        }

        val total =
            selectedCharges.fold(BigDecimal.ZERO) { amount, charge ->
                amount + charge.amountTotal
            }
        val originalTotal = selectedCharges.fold(BigDecimal.ZERO) { amount, charge ->
            amount + (charge.originalAmount ?: charge.amountTotal)
        }

        return CheckoutShippingCharge(
            serviceType = "MULTI_ORIGIN",
            serviceName = "Shipping (${selectedCharges.size} origins)" +
                if (requiresDdpEstimate) " (DDP estimate)" else "",
            amountTotal = total,
            currency = "eur",
            estimatedDeliveryDate =
                selectedCharges.mapNotNull { it.estimatedDeliveryDate }.maxOrNull(),
            originalAmount = originalTotal,
            originalCurrency = "EUR",
            originServiceTypes = selectedCharges.mapNotNull { it.serviceType }
        )
    }

    companion object {
        private val EU_COUNTRIES = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR",
            "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK",
            "SI", "ES", "SE"
        )
        private val SHIPPING_BUFFER_MULTIPLIER = BigDecimal("1.10")
    }
}
