package domain.checkout

import java.math.BigDecimal

data class CheckoutLineItem(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val amountTotal: BigDecimal,
    val currency: String = "eur",
    val imageUrl: String? = null,
    val description: String? = null
)


