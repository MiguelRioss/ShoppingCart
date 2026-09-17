package services.checkout.core

import domain.cart.ShoppingCart
import java.math.BigDecimal
import services.common.ServiceErrorCode
import services.common.ServiceException

class CheckoutValidator {

    fun validateCart(
        cart: ShoppingCart
    ) {

        if (cart.products.isEmpty()) {
            throw ServiceException(
                errorCode =
                    ServiceErrorCode.CartIsEmpty
            )
        }

        cart.products.forEach { product ->
            if (!product.isSample) {
                val squareMeters =
                    requireNotNull(product.squareMeters)

                if (!squareMeters.isMultipleOfHalfSquareMeter()) {
                    throw ServiceException(
                        errorCode =
                            ServiceErrorCode.CartQuantityNotHalfMeterMultiple,
                        description =
                            "Product ${product.productId} has " +
                                    "$squareMeters m2, " +
                                    "but checkout quantities must be multiples of 0.5 m2"
                    )
                }
            }
        }
    }

    private fun Double.isMultipleOfHalfSquareMeter(): Boolean =
        BigDecimal.valueOf(this)
            .remainder(
                BigDecimal("0.5")
            )
            .compareTo(
                BigDecimal.ZERO
            ) == 0
}