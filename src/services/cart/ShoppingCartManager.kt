/**
 * Default cart business logic for save, fetch, merge, and clear operations.
 */
package services.cart

import db.ShoppingCartRepository
import domain.cart.ShoppingCart
import domain.cart.ShoppingCartProduct
import productdatabaseaccesslayer.ProductDataAccess
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

class ShoppingCartManager(
    private val shoppingCartRepository: ShoppingCartRepository,
    private val productDataAccess: ProductDataAccess = object : ProductDataAccess {
        override fun getProductById(productId: Long): String = "{}"
    },
    private val clock: Clock = Clock.systemUTC(),
    private val purchasableProductReader: PurchasableProductReader = PurchasableProductReader(productDataAccess)
) : ShoppingCartService {
    override fun getCartByUserId(userId: UUID): ShoppingCart? =
        shoppingCartRepository.getCartByUserId(userId)

    override fun getCartBySessionId(sessionId: String): ShoppingCart? =
        shoppingCartRepository.getCartBySessionId(sessionId)

    override fun clearCartBySessionId(sessionId: String): Boolean =
        shoppingCartRepository.clearCartBySessionId(sessionId)

    override fun associateCartWithUser(sessionId: String, userId: UUID): ShoppingCart? {
        val cart = shoppingCartRepository.getCartBySessionId(sessionId) ?: return null

        return shoppingCartRepository.saveCart(cart.copy(userId = userId))
    }

    override fun createCart(sessionId: String, products: List<Pair<Long, Double>>, userId: UUID?): ShoppingCart {
        val existingCart = shoppingCartRepository.getCartBySessionId(sessionId)
        val updatedCart = ShoppingCart(
            id = existingCart?.id ?: UUID.randomUUID(),
            userId = userId ?: existingCart?.userId,
            dateTime = LocalDateTime.now(clock),
            sessionId = sessionId,
            products = products.map { (productId, quantityM2) ->
                val purchasableProduct = purchasableProductReader.load(productId)

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
}


