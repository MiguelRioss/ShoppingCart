package http

import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryUserRepository
import org.junit.jupiter.api.Test
import services.DefaultAuthService
import services.DefaultUserService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthenticatedRequestTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `creates authenticated request from authorization header`() {
        val userRepository = InMemoryUserRepository()
        val user = DefaultUserService(userRepository, clock = clock)
            .registerUser("buyer@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        val authToken = authService.login("buyer@example.com", "password-123")
        val request = HttpRequest(
            method = "GET",
            path = "/cart",
            body = "",
            headers = mapOf("Authorization" to listOf("Bearer ${authToken.token}"))
        )

        val authenticatedRequest = AuthenticatedRequest.from(request, authService)

        assertEquals(request, authenticatedRequest?.request)
        assertEquals(user, authenticatedRequest?.user)
    }

    @Test
    fun `returns null when authorization header is missing`() {
        val authService = DefaultAuthService(InMemoryUserRepository(), InMemoryAuthTokenRepository(), clock = clock)
        val request = HttpRequest(method = "GET", path = "/cart", body = "")

        assertNull(AuthenticatedRequest.from(request, authService))
    }

    @Test
    fun `returns null when bearer token is invalid`() {
        val authService = DefaultAuthService(InMemoryUserRepository(), InMemoryAuthTokenRepository(), clock = clock)
        val request = HttpRequest(
            method = "GET",
            path = "/cart",
            body = "",
            headers = mapOf("Authorization" to listOf("Bearer invalid-token"))
        )

        assertNull(AuthenticatedRequest.from(request, authService))
    }

    @Test
    fun `finds authorization header case insensitively`() {
        val userRepository = InMemoryUserRepository()
        val user = DefaultUserService(userRepository, clock = clock)
            .registerUser("buyer@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        val authToken = authService.login("buyer@example.com", "password-123")
        val request = HttpRequest(
            method = "GET",
            path = "/cart",
            body = "",
            headers = mapOf("authorization" to listOf("Bearer ${authToken.token}"))
        )

        val authenticatedRequest = AuthenticatedRequest.from(request, authService)

        assertEquals(user, authenticatedRequest?.user)
    }
}
