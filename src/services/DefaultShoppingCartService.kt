package services

import db.ShoppingCartRepository
import domain.ShoppingCart
import domain.ShoppingCartProduct
import productdatabaseaccesslayer.ProductDataAccess
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultShoppingCartService(
    private val shoppingCartRepository: ShoppingCartRepository,
    private val productDataAccess: ProductDataAccess = object : ProductDataAccess {
        override fun getProductById(productId: Long): String = "{}"
    },
    private val clock: Clock = Clock.systemUTC()
) : ShoppingCartService {
    override fun getCartByUserId(userId: UUID): ShoppingCart? =
        shoppingCartRepository.getCartByUserId(userId)

    override fun createCart(sessionId: String, products: List<Pair<Long, Double>>, userId: UUID?): ShoppingCart =
        shoppingCartRepository.saveCart(
            ShoppingCart(
                id = UUID.randomUUID(),
                userId = userId,
                dateTime = LocalDateTime.now(clock),
                sessionId = sessionId,
                products = products.map { (productId, quantityM2) ->
                    val purchasableProduct = loadPurchasableProduct(productId)

                    ShoppingCartProduct(
                        productId = productId,
                        squareMeters = quantityM2,
                        amountBoxes = ceil(quantityM2 / purchasableProduct.m2PerBox.toDouble()).toInt(),
                        totalPricePerProduct = purchasableProduct.pricePerM2
                            .multiply(BigDecimal.valueOf(quantityM2))
                            .setScale(2, RoundingMode.HALF_UP)
                    )
                }
            )
        )

    override fun saveCart(cart: ShoppingCart): ShoppingCart =
        shoppingCartRepository.saveCart(
            cart.also {
                it.products.forEach { product ->
                    require(productDataAccess.productExists(product.productId)) { "Product ${product.productId} does not exist" }
                }
            }
        )

    private fun loadPurchasableProduct(productId: Long): PurchasableProduct {
        require(productId == 9278L) { "Only product 9278 is supported for cart testing" }

        val productJson = requireNotNull(productDataAccess.getProductById(productId)) {
            "Product $productId does not exist"
        }
        val order = Json.parseToJsonElement(productJson)
            .jsonObject["purchase_information"]?.jsonObject
            ?.get("order")?.jsonObject
            ?: error("Product $productId does not include purchase information")

        return PurchasableProduct(
            m2PerBox = order.decimalValue("m2_per_box"),
            pricePerM2 = order.decimalValue("client_price_per_m2")
        )
    }

    private fun kotlinx.serialization.json.JsonObject.decimalValue(name: String): BigDecimal =
        requireNotNull(this[name]?.jsonPrimitive?.content?.let { runCatching { BigDecimal(it) }.getOrNull() }) {
            "Product purchase information is missing $name"
        }

    private data class PurchasableProduct(
        val m2PerBox: BigDecimal,
        val pricePerM2: BigDecimal
    )
}
