package domain

import java.time.LocalDateTime
import java.util.UUID

data class AuthToken(
    val token: String,
    val userId: UUID,
    val expiresAt: LocalDateTime
)
