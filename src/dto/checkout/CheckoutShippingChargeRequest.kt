package dto.checkout

import domain.checkout.CheckoutShippingCharge
import java.math.BigDecimal

data class CheckoutShippingChargeRequest(
    val serviceType: String?,
    val serviceName: String?,
    val amountTotal: BigDecimal,
    val currency: String?,
    val estimatedDeliveryDate: String?,
    val originalAmount: BigDecimal?,
    val originalCurrency: String?
) {
    fun toDomain(): CheckoutShippingCharge =
        CheckoutShippingCharge(
            serviceType = serviceType,
            serviceName = serviceName,
            amountTotal = amountTotal,
            currency = currency ?: "eur",
            estimatedDeliveryDate = estimatedDeliveryDate,
            originalAmount = originalAmount,
            originalCurrency = originalCurrency
        )
}
