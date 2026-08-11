package domain

import java.math.BigDecimal

/**
 * Product line stored in a shopping cart.
 *
 * @param productId product identifier from the external product catalog
 * @param squareMeters requested product area
 * @param amountBoxes number of boxes needed for the requested area
 * @param totalPricePerProduct calculated total price for this product line
 */
data class ShoppingCartProduct(
    val productId: Long,
    val squareMeters: Double,
    val amountBoxes: Int,
    val totalPricePerProduct: BigDecimal
)
