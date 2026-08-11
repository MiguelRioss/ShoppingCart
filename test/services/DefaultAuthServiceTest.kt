package services

import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryUserRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultAuthServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `logs in a registered user`() {
        val userRepository = InMemoryUserRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        val user = userService.registerUser("user@example.com", "password-123")
        val authTokenRepository = InMemoryAuthTokenRepository()
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)

        val authToken = authService.login(" User@Example.com ", "password-123")

        assertEquals(user.id, authToken.userId)
        assertEquals(LocalDateTime.parse("2026-08-07T14:30:00"), authToken.expiresAt)
        assertEquals(authToken, assertNotNull(authTokenRepository.getToken(authToken.token)))
    }

    @Test
    fun `does not login with invalid credentials`() {
        val userRepository = InMemoryUserRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        userService.registerUser("user@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)

        assertFailsWith<IllegalArgumentException> {
            authService.login("user@example.com", "wrong-password")
        }
    }

    @Test
    fun `gets user from bearer token`() {
        val userRepository = InMemoryUserRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        val user = userService.registerUser("user@example.com", "password-123")
        val authService = DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        val authToken = authService.login("user@example.com", "password-123")

        assertEquals(user, authService.getUserFromBearerToken("Bearer ${authToken.token}"))
    }

    @Test
    fun `returns null for expired bearer token`() {
        val userRepository = InMemoryUserRepository()
        val userService = DefaultUserService(userRepository, clock = clock)
        userService.registerUser("user@example.com", "password-123")
        val authTokenRepository = InMemoryAuthTokenRepository()
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)
        val authToken = authService.login("user@example.com", "password-123")
        val laterClock = Clock.fixed(Instant.parse("2026-08-07T14:30:01Z"), ZoneOffset.UTC)
        val laterAuthService = DefaultAuthService(userRepository, authTokenRepository, clock = laterClock)

        assertNull(laterAuthService.getUserFromBearerToken("Bearer ${authToken.token}"))
    }
}
