package http.auth

import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryUserRepository
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import services.DefaultAuthService
import services.DefaultUserService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `edits authenticated account`() {
        val userRepository = InMemoryUserRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        val user = userService.registerUser("buyer@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        val token = authService.login("buyer@example.com", "password-123")
        val handler = EditAccountHandler(authService, userService)

        val response = handler.handle(
            HttpRequest(
                method = "PUT",
                path = "/account",
                body = """
                    {
                      "firstName": "Jane2",
                      "lastName": "Smith",
                      "email": "buyer@example.com",
                      "phone": "+351 912 345 678",
                      "customerType": "private_customer",
                      "deliveryAddress": {
                        "addressLine1": "Street and house number",
                        "townOrCity": "Lisbon",
                        "postcode": "1000-001",
                        "country": "Portugal"
                      },
                      "sameAsDeliveryAddress": true
                    }
                """.trimIndent(),
                headers = mapOf("Authorization" to listOf("Bearer ${token.token}"))
            )
        )
        val body = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(200, response.statusCode)
        assertEquals(user.id.toString(), body["userId"]?.jsonPrimitive?.content)
        assertEquals("Jane2", body["firstName"]?.jsonPrimitive?.content)
        assertEquals("Jane2", userRepository.getUser(user.id)?.firstName)
    }

    @Test
    fun `deletes authenticated account`() {
        val userRepository = InMemoryUserRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        val user = userService.registerUser("buyer@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        val token = authService.login("buyer@example.com", "password-123")
        val handler = DeleteAccountHandler(authService, userService)

        val response = handler.handle(
            HttpRequest(
                method = "DELETE",
                path = "/account",
                body = "",
                headers = mapOf("Authorization" to listOf("Bearer ${token.token}"))
            )
        )

        assertEquals(200, response.statusCode)
        assertEquals("{}", response.body)
        assertNull(userRepository.getUser(user.id))
    }
}
