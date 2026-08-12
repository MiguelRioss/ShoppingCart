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

class AuthStatusHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `returns authenticated user when bearer token is valid`() {
        val userRepository = InMemoryUserRepository()
        val authTokenRepository = InMemoryAuthTokenRepository()
        val user = DefaultUserService(userRepository, clock = clock)
            .registerUser("buyer@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)
        val token = authService.login("buyer@example.com", "password-123")
        val handler = AuthStatusHandler(authService)

        val response = handler.handle(
            HttpRequest(
                method = "GET",
                path = "/auth/status",
                body = "",
                headers = mapOf("Authorization" to listOf("Bearer ${token.token}"))
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject

        assertEquals(200, response.statusCode)
        assertEquals("true", responseBody["authenticated"]?.jsonPrimitive?.content)
        assertEquals(user.id.toString(), responseBody["userId"]?.jsonPrimitive?.content)
        assertEquals(user.email, responseBody["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `returns unauthorized when bearer token is missing`() {
        val handler = AuthStatusHandler(
            DefaultAuthService(InMemoryUserRepository(), InMemoryAuthTokenRepository(), clock = clock)
        )

        val response = handler.handle(HttpRequest(method = "GET", path = "/auth/status", body = ""))

        assertEquals(401, response.statusCode)
    }
}
