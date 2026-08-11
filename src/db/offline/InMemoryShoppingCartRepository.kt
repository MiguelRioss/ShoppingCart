package db.offline

import db.ShoppingCartRepository
import domain.ShoppingCart
import java.util.UUID

class InMemoryShoppingCartRepository : ShoppingCartRepository {
    private val carts = mutableMapOf<UUID, ShoppingCart>()

    override fun getCartByUserId(userId: UUID): ShoppingCart? =
        carts.values.firstOrNull { it.userId == userId }

    override fun saveCart(cart: ShoppingCart): ShoppingCart {
        carts[cart.id] = cart
        return cart
    }
}
