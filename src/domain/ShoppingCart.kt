package domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * Shopping cart owned by a single user.
 *
 * @param id unique cart id
 * @param userId id of the user who owns the cart, when the cart is attached to an account
 * @param dateTime date/time when the cart was created or recorded
 * @param products products currently stored in the cart
 */
data class ShoppingCart(
    val id: UUID,
    val userId: UUID? = null,
    val dateTime: LocalDateTime,
    val sessionId: String? = null,
    val products: List<ShoppingCartProduct> = emptyList()
)
