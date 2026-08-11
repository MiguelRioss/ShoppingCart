package services

import db.offline.InMemoryShoppingCartRepository
import domain.ShoppingCart
import domain.ShoppingCartProduct
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class DefaultShoppingCartServiceTest {
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
}
