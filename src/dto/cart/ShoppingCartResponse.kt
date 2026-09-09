package dto.cart

import domain.cart.ShoppingCart
import domain.cart.ShoppingCartProduct
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class ShoppingCartResponse(
    val id: UUID,
    val userId: UUID?,
    val dateTime: LocalDateTime,
    val sessionId: String?,
    val products: List<ShoppingCartProductResponse>
)

data class ShoppingCartProductResponse(
    val productId: Long,
    val squareMeters: Double,
    val amountBoxes: Int,
    val totalPricePerProduct: BigDecimal
)

fun ShoppingCart.toResponse() = ShoppingCartResponse(
    id = id,
    userId = userId,
    dateTime = dateTime,
    sessionId = sessionId,
    products = products.map { it.toResponse() }
)

fun ShoppingCartProduct.toResponse() = ShoppingCartProductResponse(
    productId = productId,
    squareMeters = squareMeters,
    amountBoxes = amountBoxes,
    totalPricePerProduct = totalPricePerProduct
)
