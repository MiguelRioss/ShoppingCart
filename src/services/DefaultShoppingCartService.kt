package services

import db.ShoppingCartRepository
import domain.ShoppingCart
import java.util.UUID

class DefaultShoppingCartService(
    private val shoppingCartRepository: ShoppingCartRepository
) : ShoppingCartService {
    override fun getCartByUserId(userId: UUID): ShoppingCart? =
        shoppingCartRepository.getCartByUserId(userId)

    override fun saveCart(cart: ShoppingCart): ShoppingCart =
        shoppingCartRepository.saveCart(cart)
}
