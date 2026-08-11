package db.offline

import domain.AuthToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryAuthTokenRepositoryTest {
    private lateinit var repository: InMemoryAuthTokenRepository
    private val authToken = AuthToken(
        token = "token-123",
        userId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
        expiresAt = LocalDateTime.parse("2026-08-07T14:30:00")
    )

    @BeforeEach
    fun setUp() {
        repository = InMemoryAuthTokenRepository()
    }

    @Test
    fun `saves and gets a token`() {
        repository.saveToken(authToken)

        assertEquals(authToken, repository.getToken(authToken.token))
    }

    @Test
    fun `returns null when token does not exist`() {
        assertNull(repository.getToken("missing-token"))
    }
}
