package http.cart

import db.offline.InMemoryShoppingCartRepository
import domain.ShoppingCart
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import services.DefaultShoppingCartService
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClearCartHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `clears a cart by session id`() {
        val repository = InMemoryShoppingCartRepository()
        val service = DefaultShoppingCartService(repository, clock = clock)
        val cart = ShoppingCart(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            userId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
            dateTime = LocalDateTime.parse("2026-08-07T13:30:00"),
            sessionId = "session-123"
        )
        repository.saveCart(cart)
        val handler = ClearCartHandler(service)

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/cart/clear",
                body = """{"sessionId":"session-123"}"""
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(200, response.statusCode)
        assertEquals("Shopping cart cleared", responseBody["message"]?.jsonPrimitive?.content)
        assertEquals("session-123", responseBody["sessionId"]?.jsonPrimitive?.content)
        assertNull(repository.getCartByUserId(requireNotNull(cart.userId)))
    }

    @Test
    fun `returns not found when session has no cart`() {
        val handler = ClearCartHandler(
            DefaultShoppingCartService(InMemoryShoppingCartRepository(), clock = clock)
        )

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/cart/clear",
                body = """{"sessionId":"missing-session"}"""
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(404, response.statusCode)
        assertEquals("Shopping cart not found for sessionId", responseBody["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun `returns bad request when session id is missing`() {
        val handler = ClearCartHandler(
            DefaultShoppingCartService(InMemoryShoppingCartRepository(), clock = clock)
        )

        val response = handler.handle(HttpRequest(method = "POST", path = "/cart/clear", body = "{}"))

        assertEquals(400, response.statusCode)
    }
}
