package services

import db.UserRepository
import domain.User
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

class DefaultUserService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher = PasswordHasher(),
    private val clock: Clock = Clock.systemUTC()
) : UserService {
    override fun registerUser(email: String, password: String): User {
        val normalizedEmail = email.trim().lowercase()

        require(normalizedEmail.isNotBlank()) { "Email is required" }
        require(password.isNotBlank()) { "Password is required" }
        require(userRepository.getUserByEmail(normalizedEmail) == null) { "User already exists" }

        val user = User(
            id = UUID.randomUUID(),
            email = normalizedEmail,
            passwordHash = passwordHasher.hash(password),
            createdAt = LocalDateTime.now(clock)
        )

        return userRepository.saveUser(user)
    }

    override fun getUser(userId: UUID): User? =
        userRepository.getUser(userId)
}
