package services

import db.offline.InMemoryShoppingCartRepository
import domain.ShoppingCart
import domain.ShoppingCartProduct
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultShoppingCartServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)
    private val productDataAccess = object : ProductDataAccess {
        override fun getProductById(productId: Long): String? =
            if (productId == 9278L) {
                """
                {
                  "id": 9278,
                  "purchase_information": {
                    "order": {
                      "m2_per_box": "0.5",
                      "client_price_per_m2": "150"
                    }
                  }
                }
                """.trimIndent()
            } else {
                null
            }
    }

    @Test
    fun `creates a session cart with product quantity in m2 and calculated price`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository, productDataAccess, clock)

        val cart = service.createCart(
            sessionId = "session-123",
            products = listOf(9278L to 0.5)
        )

        assertEquals(null, cart.userId)
        assertEquals("session-123", cart.sessionId)
        assertEquals(LocalDateTime.parse("2026-08-07T13:30:00"), cart.dateTime)
        assertEquals(1, cart.products.size)
        assertEquals(9278L, cart.products[0].productId)
        assertEquals(0.5, cart.products[0].squareMeters)
        assertEquals(1, cart.products[0].amountBoxes)
        assertEquals(BigDecimal("75.00"), cart.products[0].totalPricePerProduct)
    }

    @Test
    fun `saves a cart`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository)
        val cart = ShoppingCart(
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

        assertEquals(cart, service.saveCart(cart))
    }

    @Test
    fun `gets a cart by user id`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository)
        val cart = ShoppingCart(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            userId = UUID.fromString("00000000-0000-0000-0000-000000000003"),
            dateTime = LocalDateTime.parse("2026-08-07T13:30:00")
        )
        service.saveCart(cart)

        assertEquals(cart, service.getCartByUserId(requireNotNull(cart.userId)))
    }

    @Test
    fun `returns null when user has no cart`() {
        val service = DefaultShoppingCartService(InMemoryShoppingCartRepository())

        assertNull(service.getCartByUserId(UUID.fromString("00000000-0000-0000-0000-000000000003")))
    }
}
