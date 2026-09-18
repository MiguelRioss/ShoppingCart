package services.cart

import domain.cart.ShoppingCart
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
    fun createCart(sessionId: String, products: List<CartProductInput>, userId: UUID? = null): ShoppingCart

    /**
     * Saves a cart.
     *
     * @param cart cart to persist
     * @return saved cart
     */
    fun saveCart(cart: ShoppingCart): ShoppingCart
}

/**
 * Input used when creating or updating a shopping cart product line.
 *
 * A cart product can represent either a normal product order or a sample.
 *
 * For a normal product, [quantityM2] contains the requested area in square metres
 * and [isSample] is false.
 *
 * For a sample, [quantityM2] is null, [sampleUnits] is positive, and
 * [isSample] is true.
 *
 * @property productId product identifier from the external product catalogue
 * @property quantityM2 requested quantity in square metres, or null for samples
 * @property isSample true when the cart line represents a product sample
 * @property sampleUnits requested number of samples, or null for normal products
 */
data class CartProductInput(
    val productId: Long,
    val quantityM2: Double?,
    val isSample: Boolean,
    val sampleUnits: Int? = null
)
