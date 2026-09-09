package services.checkout

import domain.cart.ShoppingCartProduct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import productdatabaseaccesslayer.ProductDataAccess

internal class CheckoutProductReader(
    private val productDataAccess: ProductDataAccess?
) {
    fun read(product: ShoppingCartProduct): CheckoutProduct {
        val fallback = CheckoutProduct(name = "Product ${product.productId}")
        val productJson = productDataAccess?.getProductById(product.productId) ?: return fallback

        return runCatching {
            val productElement = Json.parseToJsonElement(productJson).jsonObject
            CheckoutProduct(
                name = productElement["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: fallback.name,
                imageUrl = productElement["image"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            )
        }.getOrDefault(fallback)
    }
}


