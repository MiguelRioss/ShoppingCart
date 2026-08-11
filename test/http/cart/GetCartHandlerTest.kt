package http.cart

import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryShoppingCartRepository
import db.offline.InMemoryUserRepository
import domain.ShoppingCart
import domain.ShoppingCartProduct
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import services.DefaultAuthService
import services.DefaultShoppingCartService
import services.DefaultUserService
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class GetCartHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `gets cart for authenticated user`() {
        val userRepository = InMemoryUserRepository()
        val authTokenRepository = InMemoryAuthTokenRepository()
        val cartRepository = InMemoryShoppingCartRepository()
        val user = DefaultUserService(userRepository, clock = clock)
            .registerUser("buyer@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)
        val token = authService.login("buyer@example.com", "password-123")
        val cart = ShoppingCart(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            userId = user.id,
            dateTime = LocalDateTime.parse("2026-08-07T13:45:00"),
            products = listOf(
                ShoppingCartProduct(
                    productId = 1864L,
                    squareMeters = 12.5,
                    amountBoxes = 3,
                    totalPricePerProduct = BigDecimal("249.99")
                )
            )
        )
        cartRepository.saveCart(cart)
        val handler = GetCartHandler(authService, DefaultShoppingCartService(cartRepository))

        val response = handler.handle(
            HttpRequest(
                method = "GET",
                path = "/cart",
                body = "",
                headers = mapOf("Authorization" to listOf("Bearer ${token.token}"))
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(200, response.statusCode)
        assertEquals(cart.id.toString(), responseBody["id"]?.jsonPrimitive?.content)
        assertEquals(user.id.toString(), responseBody["userId"]?.jsonPrimitive?.content)
        assertEquals("1864", responseBody["products"]?.jsonArray?.first()?.jsonObject?.get("productId")?.jsonPrimitive?.content)
    }

    @Test
    fun `returns unauthorized without bearer token`() {
        val handler = GetCartHandler(
            DefaultAuthService(InMemoryUserRepository(), InMemoryAuthTokenRepository(), clock = clock),
            DefaultShoppingCartService(InMemoryShoppingCartRepository())
        )

        val response = handler.handle(HttpRequest(method = "GET", path = "/cart", body = ""))

        assertEquals(401, response.statusCode)
    }

    @Test
    fun `returns not found when authenticated user has no cart`() {
        val userRepository = InMemoryUserRepository()
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        DefaultUserService(userRepository, clock = clock).registerUser("buyer@example.com", "password-123")
        val token = authService.login("buyer@example.com", "password-123")
        val handler = GetCartHandler(authService, DefaultShoppingCartService(InMemoryShoppingCartRepository()))

        val response = handler.handle(
            HttpRequest(
                method = "GET",
                path = "/cart",
                body = "",
                headers = mapOf("Authorization" to listOf("Bearer ${token.token}"))
            )
        )

        assertEquals(404, response.statusCode)
    }
}
