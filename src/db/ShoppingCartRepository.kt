package db

import java.util.UUID

interface ShoppingCartRepository {
    fun getCart(cartId: UUID): ShoppingCart?
    fun saveCart(cart: ShoppingCart): ShoppingCart
    fun updateCart(cart: ShoppingCart): ShoppingCart
    fun deleteCart(cartId: UUID): Boolean
}
