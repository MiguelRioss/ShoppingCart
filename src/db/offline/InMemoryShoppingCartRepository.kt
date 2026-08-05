package db.offline

import db.ShoppingCart
import db.ShoppingCartRepository
import java.util.UUID

class InMemoryShoppingCartRepository : ShoppingCartRepository {
    private val carts = mutableMapOf<UUID, ShoppingCart>()

    override fun getCart(cartId: UUID): ShoppingCart? = carts[cartId]

    override fun saveCart(cart: ShoppingCart): ShoppingCart {
        carts[cart.id] = cart
        return cart
    }

    override fun updateCart(cart: ShoppingCart): ShoppingCart {
        require(carts.containsKey(cart.id)) { "Shopping cart not found" }
        carts[cart.id] = cart
        return cart
    }

    override fun deleteCart(cartId: UUID): Boolean = carts.remove(cartId) != null
}
