package dto.shipping

import domain.shipping.Incoterm
import domain.shipping.ShippingAddress
import domain.shipping.ShippingQuotation
import java.math.BigDecimal

data class ShippingQuotationRequest(
    val productId: Long,
    val quantityM2: BigDecimal,
    val recipientAddress: ShippingAddress,
    val destinationAddress: ShippingAddress,
    val incoterm: Incoterm? = null
) {
    fun toEntity(): ShippingQuotation =
        ShippingQuotation(
            productId = productId,
            quantityM2 = quantityM2,
            recipientAddress = recipientAddress,
            destinationAddress = destinationAddress,
            incoterm = incoterm
        )
}
