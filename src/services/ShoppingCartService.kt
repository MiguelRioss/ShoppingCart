package services

import domain.ShoppingCart

interface ShoppingCartService {
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
