package db.offline

import db.ShoppingCartRepository
import domain.ShoppingCart
import java.util.UUID

/**
 * In-memory cart repository used for local development and tests.
 *
 * Carts are lost when the application restarts.
 */
class InMemoryShoppingCartRepository : ShoppingCartRepository {
    private val carts = mutableMapOf<UUID, ShoppingCart>()

    /**
     * Returns the first cart owned by [userId].
     */
    override fun getCartByUserId(userId: UUID): ShoppingCart? =
        carts.values.firstOrNull { it.userId == userId }

    override fun getCartBySessionId(sessionId: String): ShoppingCart? =
        carts.values.firstOrNull { it.sessionId == sessionId }

    override fun clearCartBySessionId(sessionId: String): Boolean {
        val cart = getCartBySessionId(sessionId) ?: return false
        carts.remove(cart.id)
        return true
    }

    /**
     * Saves or replaces a cart by id.
     */
    override fun saveCart(cart: ShoppingCart): ShoppingCart {
        carts[cart.id] = cart
        return cart
    }
}
