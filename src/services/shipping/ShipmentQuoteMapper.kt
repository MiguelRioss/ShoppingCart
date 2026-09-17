package services.shipping

import domain.checkout.CheckoutShippingCharge
import java.math.BigDecimal
import shipment.core.ShipmentQuote

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
