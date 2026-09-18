package services.checkout.core

import ShoppingCartProduct
import domain.checkout.CheckoutLineItem
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import services.cart.PurchasableProductReader

class CheckoutProductResolver(
    private val purchasableProductReader: PurchasableProductReader
) {

    fun resolve(
        product: ShoppingCartProduct
    ): CheckoutLineItem {

        val purchasableProduct =
            purchasableProductReader.load(
                product.productId
            )

        if (product.isSample) {
            val sample =
                requireNotNull(purchasableProduct.sample)
            val sampleUnits =
                requireNotNull(product.sampleUnits)

            return CheckoutLineItem(
                productId = product.productId,
                name = "${purchasableProduct.name} sample",
                quantity = sampleUnits,
                amountTotal =
                    requireNotNull(sample.price)
                        .multiply(
                            BigDecimal.valueOf(sampleUnits.toLong())
                        )
                        .setScale(2, RoundingMode.HALF_UP),
                currency = "eur",
                imageUrl = purchasableProduct.imageUrl,
                description = "Product sample"
            )
        }

        requireNotNull(product.squareMeters)

        val amountBoxes =
            ceil(
                product.squareMeters /
                        purchasableProduct.m2PerBox.toDouble()
            ).toInt()
        val amountTotal =
            purchasableProduct.pricePerM2
                .multiply(
                    BigDecimal.valueOf(
                        product.squareMeters
                    )
                )
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                )

        return CheckoutLineItem(
            productId =
                product.productId,

            name =
                purchasableProduct.name,

            quantity =
                amountBoxes,

            amountTotal =
                amountTotal,

            currency =
                "eur",

            imageUrl =
                purchasableProduct.imageUrl,

            description =
                null
        )
    }
}
