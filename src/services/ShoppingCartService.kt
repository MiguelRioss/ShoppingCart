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
     * Creates a cart for a browser session.
     *
     * @param sessionId browser/session identifier associated with the cart
     * @param products product id and requested m2 quantity pairs
     * @return created cart
     */
    fun createCart(sessionId: String, products: List<Pair<Long, Double>>): ShoppingCart

    /**
     * Saves a cart.
     *
     * @param cart cart to persist
     * @return saved cart
     */
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
