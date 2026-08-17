package http.auth

import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryShoppingCartRepository
import db.offline.InMemoryUserRepository
import http.cart.GetCartHandler
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import productdatabaseaccesslayer.ProductDataAccess
import services.DefaultAuthService
import services.DefaultShoppingCartService
import services.DefaultUserService
import services.LoginService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LoginHandlerTest {
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
    fun `registered user can login through http and be recognized from bearer token later`() {
        val userRepository = InMemoryUserRepository()
        val authTokenRepository = InMemoryAuthTokenRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)
        val loginHandler = LoginHandler(LoginService(authService))
        val registeredUser = userService.registerUser("buyer@example.com", "password-123")

        val response = loginHandler.handle(
            HttpRequest(
                method = "POST",
                path = "/login",
                body = """{"email":"buyer@example.com","password":"password-123"}"""
            )
        )
        val token = Json.parseToJsonElement(response.body)
            .jsonObject["token"]
            ?.jsonPrimitive
            ?.content
        val laterClock = Clock.fixed(Instant.parse("2026-08-07T14:00:00Z"), ZoneOffset.UTC)
        val laterAuthService = DefaultAuthService(userRepository, authTokenRepository, clock = laterClock)

        assertEquals(200, response.statusCode)
        assertNotNull(token)
        assertEquals(registeredUser, laterAuthService.getUserFromBearerToken("Bearer $token"))
    }

    @Test
    fun `login associates existing session cart with bearer token user`() {
        val userRepository = InMemoryUserRepository()
        val authTokenRepository = InMemoryAuthTokenRepository()
        val cartRepository = InMemoryShoppingCartRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)
        val shoppingCartService = DefaultShoppingCartService(cartRepository, productDataAccess, clock)
        userService.registerUser("buyer@example.com", "password-123")
        shoppingCartService.createCart(
            sessionId = "browser-session-123",
            products = listOf(9278L to 0.5)
        )
        val loginHandler = LoginHandler(LoginService(authService, shoppingCartService))

        val loginResponse = loginHandler.handle(
            HttpRequest(
                method = "POST",
                path = "/login",
                body = """
                    {
                      "email": "buyer@example.com",
                      "password": "password-123",
                      "sessionId": "browser-session-123"
                    }
                """.trimIndent()
            )
        )
        val token = Json.parseToJsonElement(loginResponse.body)
            .jsonObject["token"]
            ?.jsonPrimitive
            ?.content
        val cartResponse = GetCartHandler(authService, shoppingCartService).handle(
            HttpRequest(
                method = "GET",
                path = "/cart",
                body = "",
                headers = mapOf("Authorization" to listOf("Bearer $token"))
            )
        )
        val cartBody = Json.parseToJsonElement(cartResponse.body).jsonObject

        assertEquals(200, loginResponse.statusCode)
        assertEquals(200, cartResponse.statusCode)
        assertEquals("browser-session-123", cartBody["sessionId"]?.jsonPrimitive?.content)
        assertEquals("9278", cartBody["products"]?.jsonArray?.get(0)?.jsonObject?.get("productId")?.jsonPrimitive?.content)
    }
}
