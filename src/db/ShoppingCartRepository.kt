package db

import domain.ShoppingCart

interface ShoppingCartRepository {
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
