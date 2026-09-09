package domain.shipping

import java.math.BigDecimal

data class ShippingQuotation(
    val productId: Long,
    val quantityM2: BigDecimal,
    val recipientAddress: ShippingAddress,
    val destinationAddress: ShippingAddress,
    val incoterm: Incoterm? = null,
    val options: List<ShippingOption> = emptyList()
)
