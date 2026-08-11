package db

import domain.ShoppingCart
import java.util.UUID

interface ShoppingCartRepository {
    fun getCartByUserId(userId: UUID): ShoppingCart?
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
