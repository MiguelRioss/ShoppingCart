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
import kotlinx.serialization.json.JsonObject
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

    override fun getCartBySessionId(sessionId: String): ShoppingCart? =
        shoppingCartRepository.getCartBySessionId(sessionId)

    override fun clearCartBySessionId(sessionId: String): Boolean =
        shoppingCartRepository.clearCartBySessionId(sessionId)

    override fun createCart(sessionId: String, products: List<Pair<Long, Double>>, userId: UUID?): ShoppingCart {
        val existingCart = shoppingCartRepository.getCartBySessionId(sessionId)
        val updatedCart = ShoppingCart(
            id = existingCart?.id ?: UUID.randomUUID(),
            userId = userId ?: existingCart?.userId,
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

        return shoppingCartRepository.saveCart(updatedCart)
    }

    override fun saveCart(cart: ShoppingCart): ShoppingCart =
        shoppingCartRepository.saveCart(
            cart.also {
                it.products.forEach { product ->
                    require(productDataAccess.productExists(product.productId)) { "Product ${product.productId} does not exist" }
                }
            }
        )

    private fun loadPurchasableProduct(productId: Long): PurchasableProduct {
        val productJson = productDataAccess.getProductById(productId)
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.ProductDoesNotExist,
                description = "Product $productId does not exist"
            )
        val product = Json.parseToJsonElement(productJson).jsonObject
        val order = product.purchaseOrder(productId)

        return PurchasableProduct(
            m2PerBox = order.decimalValue(productId, "m2_per_box"),
            pricePerM2 = order.decimalValue(productId, "client_price_per_m2")
        )
    }

    private fun JsonObject.purchaseOrder(productId: Long): JsonObject {
        val purchaseInformation = this["purchase_information"] as? JsonObject
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.ProductPurchaseInformationMissing,
                description = "Product $productId purchase information is missing"
            )

        return purchaseInformation["order"] as? JsonObject
            ?: throw ServiceException(
                errorCode = ServiceErrorCode.ProductOrderInformationMissing,
                description = "Product $productId order information is missing"
            )
    }

    private fun JsonObject.decimalValue(productId: Long, name: String): BigDecimal {
        val errorCode = when (name) {
            "m2_per_box" -> ServiceErrorCode.ProductM2PerBoxMissing
            "client_price_per_m2" -> ServiceErrorCode.ProductClientPricePerM2Missing
            else -> ServiceErrorCode.ProductPurchaseInformationMissing
        }

        return (
        this[name]?.jsonPrimitive?.content?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?: throw ServiceException(
                errorCode = errorCode,
                description = "Product $productId ${errorCode.defaultMessage.replaceFirstChar { it.lowercase() }}"
            )
        )
    }

    private data class PurchasableProduct(
        val m2PerBox: BigDecimal,
        val pricePerM2: BigDecimal
    )
}
