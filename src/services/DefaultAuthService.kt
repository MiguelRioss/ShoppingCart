package services

import db.AuthTokenRepository
import db.UserRepository
import domain.AuthToken
import domain.User
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * Default authentication service.
 *
 * @param userRepository source of registered users
 * @param authTokenRepository token persistence boundary
 * @param passwordHasher password verification dependency
 * @param clock clock used for token expiry checks and test determinism
 */
class DefaultAuthService(
    private val userRepository: UserRepository,
    private val authTokenRepository: AuthTokenRepository,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC()
) : AuthService {
    /**
     * Logs a user in and creates a one-hour bearer token.
     *
     * @param email email address; trimmed and lowercased before lookup
     * @param password plain text password to verify against the stored hash
     * @throws IllegalArgumentException when credentials are invalid
     */
    override fun login(email: String, password: String): AuthToken {
        val normalizedEmail = email.trim().lowercase()
        val user = requireNotNull(userRepository.getUserByEmail(normalizedEmail)) { "Invalid credentials" }

        require(passwordHasher.matches(password, user.passwordHash)) { "Invalid credentials" }

        val authToken = AuthToken(
            token = UUID.randomUUID().toString(),
            userId = user.id,
            expiresAt = LocalDateTime.now(clock).plusHours(1)
        )

        return authTokenRepository.saveToken(authToken)
    }

    /**
     * Resolves a bearer token into its user when the token exists and is not expired.
     *
     * @param bearerToken full Authorization header value, usually "Bearer <token>"
     * @return authenticated user, or null when the token cannot be used
     */
    override fun getUserFromBearerToken(bearerToken: String): User? {
        val token = bearerToken.removePrefix("Bearer ").trim()
        if (token.isBlank()) return null

        val authToken = authTokenRepository.getToken(token) ?: return null
        if (!authToken.expiresAt.isAfter(LocalDateTime.now(clock))) return null

        return userRepository.getUser(authToken.userId)
    }
}
