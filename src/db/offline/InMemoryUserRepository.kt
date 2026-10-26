package db.offline

import db.UserRepository
import domain.user.User
import java.util.UUID

/**
 * In-memory user repository used for local development and tests.
 *
 * Users are lost when the application restarts.
 */
class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<UUID, User>()

    override fun listUserSummaries(): List<domain.user.UserSummary> =
        users.values.sortedWith(compareByDescending<User> { it.createdAt }.thenBy { it.id }).map {
            domain.user.UserSummary(it.id, it.email, it.createdAt, it.firstName, it.lastName, it.phone)
        }

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

    /**
     * Removes a user by id.
     */
    override fun deleteUser(userId: UUID): Boolean =
        users.remove(userId) != null
}
