package services

import db.ShoppingCart
import java.util.UUID

interface ShoppingCartService {
    fun createCart(sessionId: UUID): ShoppingCart

    fun addItem(cartId: UUID, productId: Long, quantity: Int): ShoppingCart

    fun removeItem(cartId: UUID, productId: Long): ShoppingCart

    fun clearCart(cartId: UUID): ShoppingCart

    fun updateItemQuantity(cartId: UUID, productId: Long, quantity: Int): ShoppingCart

    fun checkout(cartId: UUID): ShoppingCart
}
