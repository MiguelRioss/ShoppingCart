package domain.checkout

import java.math.BigDecimal

/**
 * Optional shipping charge supplied by a caller before checkout creation.
 *
 * This type is intentionally carrier-neutral. A separate shipping application can
 * calculate a rate, then pass the final amount and display name into this API.
 *
 * @property serviceType internal or provider-specific service code
 * @property serviceName customer-facing shipping service name
 * @property amountTotal shipping amount in major currency units
 * @property currency currency code; defaults to `eur` when converted to a line item
 * @property estimatedDeliveryDate optional display value for the estimated delivery date
 * @property originalAmount optional source amount before conversion or normalization
 * @property originalCurrency optional source currency before conversion or normalization
 */
data class CheckoutShippingCharge(
    val serviceType: String?,
    val serviceName: String?,
    val amountTotal: BigDecimal,
    val currency: String?,
    val estimatedDeliveryDate: String? = null,
    val originalAmount: BigDecimal? = null,
    val originalCurrency: String? = null
)

/**
 * Converts a shipping charge into a payment line item.
 *
 * Shipping uses product id `0` because it is not a catalog product.
 */
fun CheckoutShippingCharge.toCheckoutLineItem(): CheckoutLineItem =
    CheckoutLineItem(
        productId = 0,
        name =
            serviceName
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: "Shipping",
        quantity = 1,
        amountTotal = amountTotal,
        currency =
            currency
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.lowercase()
                ?: "eur",
        description = null
    )
