package services.user

import domain.user.User
import dto.auth.RegisterUserRequest
import dto.auth.UpdateAccountRequest
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

    /**
     * Updates editable account fields for an existing user.
     *
     * @param userId unique user id
     * @param request requested account changes
     * @return updated user
     */
    fun updateUser(userId: UUID, request: UpdateAccountRequest): User

    /**
     * Deletes a user account.
     *
     * @param userId unique user id
     * @return true when a user was deleted
     */
    fun deleteUser(userId: UUID): Boolean
}


