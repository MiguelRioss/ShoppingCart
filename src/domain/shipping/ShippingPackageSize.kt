package domain.shipping

import java.math.BigDecimal

data class ShippingPackageSize(
    val quantity: BigDecimal,
    val length: BigDecimal,
    val width: BigDecimal,
    val height: BigDecimal,
    val packedWeight: BigDecimal,
    val packagingCost: BigDecimal?,
    val packaging: String?
)
