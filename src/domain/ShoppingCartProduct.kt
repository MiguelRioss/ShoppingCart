package domain

import java.math.BigDecimal

data class ShoppingCartProduct(
    val productId: Long,
    val squareMeters: Double,
    val amountBoxes: Int,
    val totalPricePerProduct: BigDecimal
)
