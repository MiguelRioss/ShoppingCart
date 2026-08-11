package services

import db.AuthTokenRepository
import db.UserRepository
import domain.AuthToken
import domain.User
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

class DefaultAuthService(
    private val userRepository: UserRepository,
    private val authTokenRepository: AuthTokenRepository,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC()
) : AuthService {
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

    override fun getUserFromBearerToken(bearerToken: String): User? {
        val token = bearerToken.removePrefix("Bearer ").trim()
        if (token.isBlank()) return null

        val authToken = authTokenRepository.getToken(token) ?: return null
        if (!authToken.expiresAt.isAfter(LocalDateTime.now(clock))) return null

        return userRepository.getUser(authToken.userId)
    }
}
