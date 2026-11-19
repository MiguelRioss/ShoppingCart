package domain.auth

import java.time.LocalDateTime
import java.util.UUID

/**
 * Session token issued after a successful login.
 *
 * @property token opaque bearer token value sent by clients in the Authorization header
 * @property userId id of the user that owns this token
 * @property expiresAt UTC date/time after which the token is no longer valid
 */
data class AuthToken(
    val token: String,
    val userId: UUID,
    val expiresAt: LocalDateTime
)


