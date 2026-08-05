package services

import db.offline.InMemoryShoppingCartRepository
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultShoppingCartServiceTest {
    @Test
    fun `creates and saves an empty cart`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository, ProductDataAccess { false })

        val sessionId = UUID.randomUUID()
        val cart = service.createCart(sessionId)

        assertTrue(cart.items.isEmpty())
        assertEquals(sessionId, cart.sessionId)
        assertEquals(cart, assertNotNull(repository.getCart(cart.id)))
    }

    @Test
    fun `adds an existing product to a cart`() {
        val repository = InMemoryShoppingCartRepository()
        val productId = 1864L
        val service = DefaultShoppingCartService(repository, ProductDataAccess { it == productId })
        val cart = service.createCart(UUID.randomUUID())

        val updatedCart = service.addItem(cart.id, productId, 2)

        assertEquals(listOf(db.CartItem(productId, 2)), updatedCart.items)
    }

    @Test
    fun `does not add an unknown product`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository, ProductDataAccess { false })
        val cart = service.createCart(UUID.randomUUID())

        assertFailsWith<IllegalArgumentException> {
            service.addItem(cart.id, 9999L, 1)
        }
    }
}
