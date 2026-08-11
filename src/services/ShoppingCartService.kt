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
     * Saves a cart.
     *
     * @param cart cart to persist
     * @return saved cart
     */
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
