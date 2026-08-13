package db.offline

import domain.ShoppingCart
import domain.ShoppingCartProduct
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryShoppingCartRepositoryTest {
    private lateinit var repository: InMemoryShoppingCartRepository
    private val cart = ShoppingCart(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        userId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        dateTime = LocalDateTime.parse("2026-08-07T13:30:00"),
        products = listOf(
            ShoppingCartProduct(
                productId = 1864L,
                squareMeters = 12.5,
                amountBoxes = 3,
                totalPricePerProduct = BigDecimal("249.99")
            )
        )
    )

    @BeforeEach
    fun setUp() {
        repository = InMemoryShoppingCartRepository()
    }

    @Test
    fun `saves a cart`() {
        assertEquals(cart, repository.saveCart(cart))
    }

    @Test
    fun `gets a cart by user id`() {
        repository.saveCart(cart)

        assertEquals(cart, repository.getCartByUserId(requireNotNull(cart.userId)))
    }

    @Test
    fun `returns null when user has no cart`() {
        assertNull(repository.getCartByUserId(UUID.fromString("00000000-0000-0000-0000-000000000004")))
    }
}
