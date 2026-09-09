package domain.shipping

import java.math.BigDecimal
import java.time.LocalDate

data class ShippingOption(
    val shippingProviderType: ShippingProviderType,
    val serviceCode: String,
    val serviceName: String,
    val price: BigDecimal,
    val currency: String,
    val estimatedDeliveryDate: LocalDate? = null,
    val incoterm: Incoterm? = null
)
