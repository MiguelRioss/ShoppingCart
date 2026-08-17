package services

import domain.ShoppingCart
import java.util.UUID

/**
 * Shopping-cart use cases.
 */
interface ShoppingCartService {
    /**
     * Finds the cart owned by a user.
     *
     * @param userId authenticated user's id
     * @return cart for the user, or null when none exists
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
     * Clears the cart associated with a browser/session id.
     *
     * @param sessionId browser/session identifier associated with the cart
     * @return true when a cart was cleared
     */
    fun clearCartBySessionId(sessionId: String): Boolean

    /**
     * Attaches an existing session cart to a user.
     *
     * @param sessionId browser/session identifier associated with the cart
     * @param userId authenticated user's id
     * @return the associated cart, or null when no session cart exists
     */
    fun associateCartWithUser(sessionId: String, userId: UUID): ShoppingCart?

    /**
     * Creates a cart for a browser session.
     *
     * @param sessionId browser/session identifier associated with the cart
     * @param products product id and requested m2 quantity pairs
     * @return created cart
     */
    fun createCart(sessionId: String, products: List<Pair<Long, Double>>, userId: UUID? = null): ShoppingCart

    /**
     * Saves a cart.
     *
     * @param cart cart to persist
     * @return saved cart
     */
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
