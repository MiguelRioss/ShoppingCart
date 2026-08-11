package services

import db.offline.InMemoryUserRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DefaultUserServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-08-07T13:30:00Z"), ZoneOffset.UTC)

    @Test
    fun `registers a user`() {
        val repository = InMemoryUserRepository()
        val service = DefaultUserService(repository, clock = clock)

        val user = service.registerUser(" User@Example.com ", "password-123")

        assertEquals("user@example.com", user.email)
        assertEquals("9dc051f92b72a97c298db1964a646a0c43fb5599f26c4d471d58e8fc34d3f16d", user.passwordHash)
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
}
