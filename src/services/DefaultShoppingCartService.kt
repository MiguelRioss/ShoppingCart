package services

import db.ShoppingCart
import db.ShoppingCartRepository
import db.CartItem
import productdatabaseaccesslayer.ProductDataAccess
import java.util.UUID

class DefaultShoppingCartService(
    private val shoppingCartRepository: ShoppingCartRepository,
    private val productDataAccess: ProductDataAccess
) : ShoppingCartService {
    override fun createCart(sessionId: UUID): ShoppingCart {
        val cart = ShoppingCart(id = UUID.randomUUID(), sessionId = sessionId)
        return shoppingCartRepository.saveCart(cart)
    }

    override fun addItem(cartId: UUID, productId: Long, quantity: Int): ShoppingCart {
        require(quantity > 0) { "Quantity must be greater than zero" }
        require(productDataAccess.productExists(productId)) { "Product not found" }

        val cart = requireNotNull(shoppingCartRepository.getCart(cartId)) { "Shopping cart not found" }
        val updatedCart = cart.copy(items = cart.items + CartItem(productId, quantity))
        return shoppingCartRepository.updateCart(updatedCart)
    }

    override fun removeItem(cartId: UUID, productId: Long): ShoppingCart =
        TODO("Not implemented yet")

    override fun clearCart(cartId: UUID): ShoppingCart =
        TODO("Not implemented yet")

    override fun updateItemQuantity(cartId: UUID, productId: Long, quantity: Int): ShoppingCart =
        TODO("Not implemented yet")

    override fun checkout(cartId: UUID): ShoppingCart =
        TODO("Not implemented yet")
}
