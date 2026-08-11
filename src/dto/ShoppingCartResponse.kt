package dto

import domain.ShoppingCart
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class ShoppingCartResponse(
    val cart: ShoppingCart
) {
    fun toJson(): String =
        buildJsonObject {
            put("id", JsonPrimitive(cart.id.toString()))
            put("userId", JsonPrimitive(cart.userId.toString()))
            put("dateTime", JsonPrimitive(cart.dateTime.toString()))
            put(
                "products",
                buildJsonArray {
                    cart.products.forEach { product ->
                        add(
                            buildJsonObject {
                                put("productId", JsonPrimitive(product.productId))
                                put("squareMeters", JsonPrimitive(product.squareMeters))
                                put("amountBoxes", JsonPrimitive(product.amountBoxes))
                                put("totalPricePerProduct", JsonPrimitive(product.totalPricePerProduct.toPlainString()))
                            }
                        )
                    }
                }
            )
        }.toString()
}
