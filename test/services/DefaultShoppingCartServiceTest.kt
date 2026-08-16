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
import kotlin.test.assertFailsWith
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
    fun `updates existing cart when session id already exists`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository, productDataAccess, clock)
        val firstCart = service.createCart(
            sessionId = "session-123",
            products = listOf(9278L to 0.5)
        )

        val updatedCart = service.createCart(
            sessionId = "session-123",
            products = listOf(9278L to 1.5)
        )

        assertEquals(firstCart.id, updatedCart.id)
        assertEquals("session-123", updatedCart.sessionId)
        assertEquals(1, updatedCart.products.size)
        assertEquals(1.5, updatedCart.products[0].squareMeters)
        assertEquals(BigDecimal("225.00"), updatedCart.products[0].totalPricePerProduct)
        assertEquals(updatedCart, repository.getCartBySessionId("session-123"))
    }

    @Test
    fun `returns clear error when product purchase information is null`() {
        val productDataAccess = object : ProductDataAccess {
            override fun getProductById(productId: Long): String =
                """
                {
                  "id": 1864,
                  "purchase_information": null
                }
                """.trimIndent()
        }
        val service = DefaultShoppingCartService(InMemoryShoppingCartRepository(), productDataAccess, clock)

        val error = assertFailsWith<ServiceException> {
            service.createCart(
                sessionId = "session-123",
                products = listOf(1864L to 1.0)
            )
        }

        assertEquals(ServiceErrorCode.ProductPurchaseInformationMissing, error.errorCode)
        assertEquals("Product purchase information is missing", error.message)
        assertEquals("Product 1864 purchase information is missing", error.description)
    }

    @Test
    fun `returns clear error when product order information is null`() {
        val productDataAccess = object : ProductDataAccess {
            override fun getProductById(productId: Long): String =
                """
                {
                  "id": 1864,
                  "purchase_information": {
                    "order": null
                  }
                }
                """.trimIndent()
        }
        val service = DefaultShoppingCartService(InMemoryShoppingCartRepository(), productDataAccess, clock)

        val error = assertFailsWith<ServiceException> {
            service.createCart(
                sessionId = "session-123",
                products = listOf(1864L to 1.0)
            )
        }

        assertEquals(ServiceErrorCode.ProductOrderInformationMissing, error.errorCode)
        assertEquals("Product order information is missing", error.message)
        assertEquals("Product 1864 order information is missing", error.description)
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
