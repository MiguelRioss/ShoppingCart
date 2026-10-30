package db.offline

import db.ShoppingCartRepository
import domain.cart.ShoppingCart
import java.util.UUID

/**
 * In-memory cart repository used for local development and tests.
 *
 * Carts are lost when the application restarts.
 */
class InMemoryShoppingCartRepository : ShoppingCartRepository {
    private val carts = mutableMapOf<UUID, ShoppingCart>()
    @Synchronized override fun getCartById(cartId: UUID): ShoppingCart? = carts[cartId]
    @Synchronized override fun clearCartIfUnchanged(cartId: UUID, savedAt: java.time.LocalDateTime): Boolean {
        val cart = carts[cartId] ?: return false
        if (cart.dateTime != savedAt) return false
        carts.remove(cartId)
        return true
    }

    /**
     * Returns the most recently saved cart owned by [userId].
     */
    @Synchronized override fun getCartByUserId(userId: UUID): ShoppingCart? =
        carts.values.filter { it.userId == userId }.maxWithOrNull(compareBy<ShoppingCart> { it.dateTime }.thenBy { it.id })

    @Synchronized override fun getCartBySessionId(sessionId: String): ShoppingCart? =
        carts.values.firstOrNull { it.sessionId == sessionId }

    @Synchronized override fun clearCartBySessionId(sessionId: String): Boolean {
        val cart = getCartBySessionId(sessionId) ?: return false
        carts.remove(cart.id)
        return true
    }

    /**
     * Updates only the matching session cart; other carts for the user remain independent.
     */
    @Synchronized override fun saveCart(cart: ShoppingCart): ShoppingCart {
        val existing = cart.sessionId?.let(::getCartBySessionId)
        require(existing?.userId == null || existing.userId == cart.userId) { "This shopping cart belongs to another user" }
        val savedCart = existing?.let { cart.copy(id = it.id) } ?: cart
        carts[savedCart.id] = savedCart
        return savedCart
    }
}
