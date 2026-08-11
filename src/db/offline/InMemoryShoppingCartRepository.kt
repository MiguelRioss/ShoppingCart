package db.offline

import db.ShoppingCartRepository
import domain.ShoppingCart
import java.util.UUID

class InMemoryShoppingCartRepository : ShoppingCartRepository {
    private val carts = mutableMapOf<UUID, ShoppingCart>()

    override fun saveCart(cart: ShoppingCart): ShoppingCart {
        carts[cart.id] = cart
        return cart
    }
}
