package http.client

import db.offline.InMemoryAuthTokenRepository
import db.offline.InMemoryUserRepository
import domain.user.User
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import services.auth.AuthManager
import services.auth.PasswordHasher
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

class GetClientInfoHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `returns authenticated client info`() {
        val userRepository = InMemoryUserRepository()
        val authTokenRepository = InMemoryAuthTokenRepository()
        val user = userRepository.saveUser(
            User(
                id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
                email = "buyer@example.com",
                passwordHash = PasswordHasher().hash("password-123"),
                createdAt = LocalDateTime.parse("2026-08-07T13:30:00"),
                firstName = "Jane",
                lastName = "Smith",
                phone = "+351 912 345 678",
                customerType = "PrivateCustomer",
                deliveryAddressLine1 = "Street and house number",
                deliveryTownOrCity = "Lisbon",
                deliveryPostcode = "1000-001",
                deliveryCountry = "Portugal",
                vatNumber = "PT123456789"
            )
        )
        val authService = AuthManager(userRepository, authTokenRepository, clock = clock)
        val token = authService.login(user.email, "password-123")
        val handler = GetClientInfoHandler(authService)

        val response = handler.handle(
            HttpRequest(
                method = "GET",
                path = "/client/info",
                body = "",
                headers = mapOf("Authorization" to listOf("Bearer ${token.token}"))
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject
        assertEquals(200, response.statusCode)
        assertEquals(user.id.toString(), responseBody["id"]?.jsonPrimitive?.content)
        assertEquals("buyer@example.com", responseBody["email"]?.jsonPrimitive?.content)
        assertEquals("2026-08-07T13:30", responseBody["createdAt"]?.jsonPrimitive?.content)
        assertEquals("Jane", responseBody["firstName"]?.jsonPrimitive?.content)
        assertEquals("Smith", responseBody["lastName"]?.jsonPrimitive?.content)
        assertEquals("+351 912 345 678", responseBody["phone"]?.jsonPrimitive?.content)
        assertEquals("PrivateCustomer", responseBody["customerType"]?.jsonPrimitive?.content)
        assertEquals("Street and house number", responseBody["deliveryAddressLine1"]?.jsonPrimitive?.content)
        assertEquals("Lisbon", responseBody["deliveryTownOrCity"]?.jsonPrimitive?.content)
        assertEquals("1000-001", responseBody["deliveryPostcode"]?.jsonPrimitive?.content)
        assertEquals("Portugal", responseBody["deliveryCountry"]?.jsonPrimitive?.content)
        assertEquals("PT123456789", responseBody["vatNumber"]?.jsonPrimitive?.content)
    }

    @Test
    fun `returns unauthorized without bearer token`() {
        val handler = GetClientInfoHandler(
            AuthManager(InMemoryUserRepository(), InMemoryAuthTokenRepository(), clock = clock)
        )

        val response = handler.handle(HttpRequest(method = "GET", path = "/client/info", body = ""))

        assertEquals(401, response.statusCode)
    }
}


