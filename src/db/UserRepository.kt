package db

import domain.User
import java.util.UUID

/**
 * Persistence boundary for users.
 */
interface UserRepository {
    /**
     * Stores a user.
     *
     * @param user user domain object to persist
     * @return the saved user
     */
    fun saveUser(user: User): User

    /**
     * Finds a user by id.
     *
     * @param userId unique user id
     * @return matching user, or null when no user exists
     */
    fun getUser(userId: UUID): User?

    /**
     * Finds a user by normalized email address.
     *
     * @param email normalized email address
     * @return matching user, or null when no user exists
     */
    fun getUserByEmail(email: String): User?
}
