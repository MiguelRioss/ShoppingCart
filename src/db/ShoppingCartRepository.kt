package db

import domain.cart.ShoppingCart
import java.util.UUID

/**
 * Persistence boundary for shopping carts.
 */
interface ShoppingCartRepository {
    fun getCartById(cartId: UUID): ShoppingCart?
    /** Deletes only the exact saved version, preserving edits made after checkout. */
    fun clearCartIfUnchanged(cartId: UUID, savedAt: java.time.LocalDateTime): Boolean

    /**
     * Finds the most recently saved cart belonging to a user.
     *
     * @param userId id of the cart owner
     * @return the user's cart, or null when the user has no cart
     */
    fun getCartByUserId(userId: UUID): ShoppingCart?

    /**
     * Finds the cart associated with a browser/session id.
     *
     * @param sessionId browser/session identifier associated with the cart
     * @return the session cart, or null when none exists
     */
    fun getCartBySessionId(sessionId: String): ShoppingCart?

    /**
     * Deletes the cart associated with a browser/session id.
     *
     * @param sessionId browser/session identifier associated with the cart
     * @return true when a cart was deleted
     */
    fun clearCartBySessionId(sessionId: String): Boolean

    /**
     * Stores a shopping cart.
     *
     * @param cart cart domain object to persist
     * @return the saved cart
     */
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
