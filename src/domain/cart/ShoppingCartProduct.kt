import java.math.BigDecimal

/**
 * Product line stored in a shopping cart.
 *
 * A cart line can represent either a normal product order or a product sample.
 *
 * For a normal product, [squareMeters] and [amountBoxes] contain the requested
 * area and required number of boxes, and [isSample] is false.
 *
 * For a sample, [squareMeters] and [amountBoxes] are null, and [isSample] is true.
 *
 * @property productId product identifier from the external product catalogue
 * @property squareMeters requested product area in square metres, or null for samples
 * @property amountBoxes number of boxes required, or null for samples
 * @property totalPricePerProduct total price calculated for this cart line
 * @property isSample true when this cart line represents a product sample
 */
data class ShoppingCartProduct(
    val productId: Long,
    val squareMeters: Double?,
    val amountBoxes: Int?,
    val totalPricePerProduct: BigDecimal,
    val isSample: Boolean
)