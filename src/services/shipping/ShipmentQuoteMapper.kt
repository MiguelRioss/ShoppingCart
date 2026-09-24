package services.shipping

import domain.checkout.CheckoutShippingCharge
import java.math.BigDecimal
import domain.shipment.ShipmentQuote

/** Preserves a selected provider quote as the shipping charge used at checkout. */
fun ShipmentQuote.toCheckoutShippingCharge(): CheckoutShippingCharge =
    CheckoutShippingCharge(
        serviceType = serviceType,
        serviceName = serviceName,
        amountTotal = BigDecimal.valueOf(amount),
        currency = currency.lowercase(),
        estimatedDeliveryDate = estimatedDeliveryDate,
        originalAmount = BigDecimal.valueOf(amount),
        originalCurrency = currency
    )
