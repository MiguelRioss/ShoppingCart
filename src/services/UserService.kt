package services

import domain.User
import dto.RegisterUserRequest
import java.util.UUID

/**
 * User-management use cases.
 */
interface UserService {
    /**
     * Creates a new user account.
     *
     * @param email email address; implementations normalize it before saving
     * @param password plain text password to hash before persistence
     * @return created user
     */
    fun registerUser(email: String, password: String): User

    fun registerUser(request: RegisterUserRequest): User

    /**
     * Finds a user by id.
     *
     * @param userId unique user id
     * @return matching user, or null when it does not exist
     */
    fun getUser(userId: UUID): User?
}
