package services

import domain.ShoppingCart
import java.util.UUID

interface ShoppingCartService {
    fun getCartByUserId(userId: UUID): ShoppingCart?
    fun saveCart(cart: ShoppingCart): ShoppingCart
}
