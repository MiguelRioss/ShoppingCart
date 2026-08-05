package db.offline

import db.CartItem
import db.ShoppingCart
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryShoppingCartRepositoryTest {
    private lateinit var repository: InMemoryShoppingCartRepository
    private val cart = ShoppingCart(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        UUID.fromString("00000000-0000-0000-0000-000000000003"),
        listOf(CartItem(1864L, 1))
    )

    @BeforeEach
    fun setUp() {
        repository = InMemoryShoppingCartRepository()
    }

    @Test
    fun `saves and gets a cart`() {
        repository.saveCart(cart)

        assertEquals(cart, repository.getCart(cart.id))
    }

    @Test
    fun `updates an existing cart`() {
        repository.saveCart(cart)
        val updatedCart = cart.copy(items = listOf(CartItem(cart.items.first().productId, 2)))

        repository.updateCart(updatedCart)

        assertEquals(updatedCart, repository.getCart(cart.id))
    }

    @Test
    fun `deletes an existing cart`() {
        repository.saveCart(cart)

        assertTrue(repository.deleteCart(cart.id))
        assertNull(repository.getCart(cart.id))
    }

    @Test
    fun `returns false when deleting an unknown cart`() {
        assertFalse(repository.deleteCart(UUID.randomUUID()))
    }
}
