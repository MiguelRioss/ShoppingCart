package services

import db.ShoppingCartRepository
import domain.ShoppingCart

class DefaultShoppingCartService(
    private val shoppingCartRepository: ShoppingCartRepository
) : ShoppingCartService {
    override fun saveCart(cart: ShoppingCart): ShoppingCart =
        shoppingCartRepository.saveCart(cart)
}
