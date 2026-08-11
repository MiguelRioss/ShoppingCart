package db.offline

import db.UserRepository
import domain.User
import java.util.UUID

/**
 * In-memory user repository used for local development and tests.
 *
 * Users are lost when the application restarts.
 */
class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<UUID, User>()

    /**
     * Saves or replaces a user by id.
     */
    override fun saveUser(user: User): User {
        users[user.id] = user
        return user
    }

    /**
     * Returns a user by id.
     */
    override fun getUser(userId: UUID): User? = users[userId]

    /**
     * Returns the first user with the exact normalized email address.
     */
    override fun getUserByEmail(email: String): User? =
        users.values.firstOrNull { it.email == email }
}
