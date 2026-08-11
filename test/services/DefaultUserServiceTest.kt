package services

import db.offline.InMemoryUserRepository
import dto.CustomerType
import dto.RegisterAddressRequest
import dto.RegisterUserRequest
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class DefaultUserServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `registers a user`() {
        val repository = InMemoryUserRepository()
        val service = DefaultUserService(repository, clock = clock)

        val user = service.registerUser(" User@Example.com ", "password-123")

        assertEquals("user@example.com", user.email)
        assertTrue(user.passwordHash.startsWith("$2a$"))
        assertTrue(PasswordHasher().matches("password-123", user.passwordHash))
        assertEquals(LocalDateTime.parse("2026-08-07T13:30:00"), user.createdAt)
        assertEquals(user, assertNotNull(repository.getUser(user.id)))
    }

    @Test
    fun `gets a registered user`() {
        val service = DefaultUserService(InMemoryUserRepository(), clock = clock)
        val user = service.registerUser("user@example.com", "password-123")

        assertEquals(user, service.getUser(user.id))
    }

    @Test
    fun `does not register the same email twice`() {
        val service = DefaultUserService(InMemoryUserRepository(), clock = clock)

        service.registerUser("user@example.com", "password-123")

        assertFailsWith<IllegalArgumentException> {
            service.registerUser("USER@example.com", "password-456")
        }
    }

    @Test
    fun `registers a user with all required customer fields`() {
        val repository = InMemoryUserRepository()
        val service = DefaultUserService(repository, clock = clock)
        val request = RegisterUserRequest(
            firstName = " Jane ",
            lastName = " Smith ",
            email = " Buyer@Example.com ",
            password = "password-123",
            phone = " +351 912 345 678 ",
            customerType = CustomerType.PrivateCustomer,
            deliveryAddress = RegisterAddressRequest(
                company = null,
                addressLine1 = "Street and house number",
                addressLine2 = null,
                townOrCity = "Lisbon",
                postcode = "1000-001",
                country = "Portugal"
            ),
            sameAsDeliveryAddress = true,
            invoiceAddress = RegisterAddressRequest(
                company = null,
                addressLine1 = "Street and house number",
                addressLine2 = null,
                townOrCity = "Lisbon",
                postcode = "1000-001",
                country = "Portugal"
            ),
            vatNumber = null,
            projectNotes = null
        )

        val user = service.registerUser(request)

        assertEquals("buyer@example.com", user.email)
        assertEquals("Jane", user.firstName)
        assertEquals("Smith", user.lastName)
        assertEquals("+351 912 345 678", user.phone)
        assertEquals("PrivateCustomer", user.customerType)
        assertEquals("Street and house number", user.deliveryAddressLine1)
        assertEquals("Lisbon", user.deliveryTownOrCity)
        assertEquals("1000-001", user.deliveryPostcode)
        assertEquals("Portugal", user.deliveryCountry)
        assertEquals(true, user.sameAsDeliveryAddress)
        assertTrue(PasswordHasher().matches("password-123", user.passwordHash))
        assertEquals(user, assertNotNull(repository.getUser(user.id)))
    }
}
