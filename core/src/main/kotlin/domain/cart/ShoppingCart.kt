package domain.cart

import ShoppingCartProduct
import java.time.LocalDateTime
import java.util.UUID

/**
 * Shopping cart owned by a single user.
 *
 * @property id unique cart id
 * @property userId id of the user who owns the cart, when attached to an account
 * @property dateTime date/time when the cart was created or last saved
 * @property sessionId browser or application session id for guest cart lookup
 * @property products products currently stored in the cart
 */
data class ShoppingCart(
    val id: UUID,
    val userId: UUID? = null,
    val dateTime: LocalDateTime,
    val sessionId: String? = null,
    val products: List<ShoppingCartProduct> = emptyList()
)


