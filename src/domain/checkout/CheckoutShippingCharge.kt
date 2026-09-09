package domain.checkout

import java.math.BigDecimal

data class CheckoutShippingCharge(
    val serviceType: String?,
    val serviceName: String?,
    val amountTotal: BigDecimal,
    val currency: String = "eur",
    val estimatedDeliveryDate: String? = null,
    val originalAmount: BigDecimal? = null,
    val originalCurrency: String? = null
)


