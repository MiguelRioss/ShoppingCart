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
import services.LoginService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LoginHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

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
}
