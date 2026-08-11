package db.offline

import domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryUserRepositoryTest {
    private lateinit var repository: InMemoryUserRepository
    private val user = User(
        id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
        email = "user@example.com",
        passwordHash = "password-hash",
        createdAt = LocalDateTime.parse("2026-08-07T13:30:00")
    )

    @BeforeEach
    fun setUp() {
        repository = InMemoryUserRepository()
    }

    @Test
    fun `saves and gets a user by id`() {
        repository.saveUser(user)

        assertEquals(user, repository.getUser(user.id))
    }

    @Test
    fun `gets a user by email`() {
        repository.saveUser(user)

        assertEquals(user, repository.getUserByEmail(user.email))
    }

    @Test
    fun `returns null when user does not exist`() {
        assertNull(repository.getUser(UUID.randomUUID()))
        assertNull(repository.getUserByEmail("missing@example.com"))
    }
}
