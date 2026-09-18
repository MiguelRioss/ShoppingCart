/**
 * Default cart business logic for save, fetch, merge, and clear operations.
 */
package services.cart

import ShoppingCartProduct
import db.ShoppingCartRepository
import domain.cart.ShoppingCart
import productdatabaseaccesslayer.ProductDataAccess
import services.common.ServiceErrorCode
import services.common.ServiceException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

class ShoppingCartManager(
    private val shoppingCartRepository: ShoppingCartRepository,
    private val productDataAccess: ProductDataAccess = object : ProductDataAccess {
        override fun getAllProducts(): String = "[]"

        override fun getPurchasableProducts(): String = "[]"

        override fun getProductById(productId: Long): String = "{}"

        override fun getProductBySlug(productSlug: String): String = "{}"
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


    override fun createCart(
        sessionId: String,
        products: List<CartProductInput>,
        userId: UUID?
    ): ShoppingCart {
        val existingCart =
            shoppingCartRepository.getCartBySessionId(sessionId)

        val updatedCart = ShoppingCart(
            id = existingCart?.id ?: UUID.randomUUID(),
            userId = userId ?: existingCart?.userId,
            dateTime = LocalDateTime.now(clock),
            sessionId = sessionId,

            products = products.map { product ->

               validateProduct(product)

                val purchasableProduct =
                    purchasableProductReader.load(product.productId)

                val quantityM2 =
                    if (product.isSample) {
                        null
                    } else {
                        requireNotNull(product.quantityM2)
                    }

                val amountBoxes =
                    quantityM2?.let {
                        ceil(
                            it / purchasableProduct.m2PerBox.toDouble()
                        ).toInt()
                    }

                val totalPrice =
                    if (product.isSample) {
                        val sample =
                            requireNotNull(purchasableProduct.sample)

                        if (!sample.available) {
                            throw ServiceException(
                                errorCode = ServiceErrorCode.ProductSampleUnavailable,
                                description =
                                    "A sample is not available for product ${product.productId}"
                            )
                        }

                        val sampleUnits =
                            requireNotNull(product.sampleUnits)

                        val maxQuantity =
                            requireNotNull(sample.maxQuantity) {
                                "Sample maximum quantity is missing for product ${product.productId}"
                            }

                        if (sampleUnits > maxQuantity) {
                            throw ServiceException(
                                errorCode = ServiceErrorCode.SampleMaximumQuantityExceeded,
                                description =
                                    "Product ${product.productId} allows at most $maxQuantity sample units"
                            )
                        }

                        requireNotNull(sample.price)
                            .multiply(
                                BigDecimal.valueOf(sampleUnits.toLong())
                            )
                            .setScale(2, RoundingMode.HALF_UP)
                    } else {
                        purchasableProduct.pricePerM2
                            .multiply(BigDecimal.valueOf(requireNotNull(quantityM2)))
                            .setScale(2, RoundingMode.HALF_UP)
                    }

                ShoppingCartProduct(
                    productId = product.productId,
                    squareMeters = quantityM2,
                    amountBoxes = amountBoxes,
                    totalPricePerProduct = totalPrice,
                    isSample = product.isSample,
                    sampleUnits = product.sampleUnits
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

private fun validateProduct(
    product: CartProductInput
) {
    if (product.isSample) {
        if (
            product.quantityM2 != null ||
            product.sampleUnits == null ||
            product.sampleUnits <= 0
        ) {
            throw ServiceException(
                errorCode = ServiceErrorCode.SampleUnitsInvalid,
                description =
                    "Product ${product.productId} requires sampleUnits greater than zero and no quantityM2"
            )
        }
        return
    }

    if (product.sampleUnits != null) {
        throw ServiceException(
            errorCode = ServiceErrorCode.SampleQuantityNotAllowed,
            description =
                "Product ${product.productId} is not a sample and must not include sampleUnits"
        )
    }

    if (
        product.quantityM2 == null ||
        product.quantityM2 <= 0.0
    ) {
        throw ServiceException(
            errorCode =
                ServiceErrorCode.CartProductQuantityInvalid,
            description =
                "Product ${product.productId} requires quantityM2 greater than zero"
        )
    }
}
