package services

import domain.AuthToken
import domain.User

/**
 * Authentication use cases for login and bearer-token lookup.
 */
interface AuthService {
    /**
     * Validates credentials and creates a new token.
     *
     * @param email user email address
     * @param password plain text password supplied by the user
     * @return newly issued auth token
     */
    fun login(email: String, password: String): AuthToken

    /**
     * Resolves an Authorization header into a user.
     *
     * @param bearerToken header value in the form "Bearer <token>"
     * @return authenticated user, or null when token is missing, invalid, or expired
     */
    fun getUserFromBearerToken(bearerToken: String): User?
}
