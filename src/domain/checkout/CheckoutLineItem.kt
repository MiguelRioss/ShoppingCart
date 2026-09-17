package domain.checkout

import java.math.BigDecimal

/**
 * A normalized line item that can be sent to a checkout/payment provider.
 *
 * @property productId product catalog id, or `0` for non-product charges such as shipping
 * @property name customer-facing item name
 * @property quantity number of units sent to the payment provider
 * @property amountTotal total amount for this line item in major currency units
 * @property currency ISO-style lowercase currency code used by the payment provider
 * @property imageUrl optional public image URL for product checkout display
 * @property description optional customer-facing item description
 */
data class CheckoutLineItem(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val amountTotal: BigDecimal,
    val currency: String = "eur",
    val imageUrl: String? = null,
    val description: String? = null
)


