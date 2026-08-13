package http.auth

import db.offline.InMemoryUserRepository
import db.offline.InMemoryAuthTokenRepository
import http.HttpRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import services.DefaultAuthService
import services.DefaultUserService
import services.PasswordHasher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RegisterHandlerTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)
    private val completeRegisterJson = """
        {
          "firstName": "Jane",
          "lastName": "Smith",
          "email": " Buyer@Example.com ",
          "password": "password-123",
          "phone": "+351 912 345 678",
          "customerType": "private_customer",
          "deliveryAddress": {
            "addressLine1": "Street and house number",
            "townOrCity": "Lisbon",
            "postcode": "1000-001",
            "country": "Portugal"
          }
        }
    """.trimIndent()

    @Test
    fun `registers a user`() {
        val userRepository = InMemoryUserRepository()
        val authTokenRepository = InMemoryAuthTokenRepository()
        val authService = DefaultAuthService(userRepository, authTokenRepository, clock = clock)
        val handler = RegisterHandler(DefaultUserService(userRepository, clock = clock), authService)

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/register",
                body = completeRegisterJson
            )
        )
        val responseBody = Json.parseToJsonElement(response.body).jsonObject
        val user = assertNotNull(userRepository.getUserByEmail("buyer@example.com"))

        assertEquals(201, response.statusCode)
        assertNotNull(responseBody["token"]?.jsonPrimitive?.content)
        assertEquals(user.id.toString(), responseBody["userId"]?.jsonPrimitive?.content)
        assertEquals("2026-08-07T14:30", responseBody["expiresAt"]?.jsonPrimitive?.content)
        assertEquals("Jane", user.firstName)
        assertEquals("Smith", user.lastName)
        assertEquals("+351 912 345 678", user.phone)
        assertEquals("PrivateCustomer", user.customerType)
        assertEquals("Street and house number", user.deliveryAddressLine1)
        assertEquals("Lisbon", user.deliveryTownOrCity)
        assertEquals("1000-001", user.deliveryPostcode)
        assertEquals("Portugal", user.deliveryCountry)
        assertTrue(PasswordHasher().matches("password-123", user.passwordHash))
    }

    @Test
    fun `returns bad request when email is missing`() {
        val userRepository = InMemoryUserRepository()
        val handler = RegisterHandler(
            DefaultUserService(userRepository, clock = clock),
            DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
        )

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/register",
                body = """{"password":"password-123"}"""
            )
        )

        assertEquals(400, response.statusCode)
    }

    @Test
    fun `returns bad request when any required registration field is missing`() {
        val requiredFields = listOf(
            "firstName",
            "lastName",
            "email",
            "password",
            "phone",
            "customerType",
            "deliveryAddress",
            "deliveryAddress.addressLine1",
            "deliveryAddress.townOrCity",
            "deliveryAddress.postcode",
            "deliveryAddress.country"
        )

        requiredFields.forEach { missingField ->
            val userRepository = InMemoryUserRepository()
            val handler = RegisterHandler(
                DefaultUserService(userRepository, clock = clock),
                DefaultAuthService(userRepository, InMemoryAuthTokenRepository(), clock = clock)
            )
            val response = handler.handle(
                HttpRequest(
                    method = "POST",
                    path = "/register",
                    body = registerJsonWithout(missingField)
                )
            )

            assertEquals(400, response.statusCode, "Expected missing $missingField to fail")
        }
    }

    private fun registerJsonWithout(field: String): String {
        val fields = mutableListOf(
            """"firstName": "Jane"""",
            """"lastName": "Smith"""",
            """"email": "buyer@example.com"""",
            """"password": "password-123"""",
            """"phone": "+351 912 345 678"""",
            """"customerType": "private_customer""""
        )
        val addressFields = mutableListOf(
            """"addressLine1": "Street and house number"""",
            """"townOrCity": "Lisbon"""",
            """"postcode": "1000-001"""",
            """"country": "Portugal""""
        )

        fields.removeIf { it.startsWith(""""$field"""") }
        if (field.startsWith("deliveryAddress.")) {
            val addressField = field.substringAfter(".")
            addressFields.removeIf { it.startsWith(""""$addressField"""") }
        }
        if (field != "deliveryAddress") {
            fields.add(""""deliveryAddress": { ${addressFields.joinToString(", ")} }""")
        }

        return "{ ${fields.joinToString(", ")} }"
    }
}
