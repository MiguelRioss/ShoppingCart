package domain.user

import java.time.LocalDateTime
import java.util.UUID

/** Fields permitted in the admin user list. Password hashes are never selected. */
data class UserSummary(
    val id: UUID,
    val email: String,
    val createdAt: LocalDateTime,
    val firstName: String?,
    val lastName: String?,
    val phone: String?
)
