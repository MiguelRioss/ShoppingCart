package services.auth

import dto.auth.LoginResponse

/**
 * Login use cases.
 */
interface LoginServiceInterface {
    /**
     * Validates credentials and creates a login response.
     *
     * @param email nullable email from the HTTP request body
     * @param password nullable password from the HTTP request body
     * @param sessionId optional browser/session identifier for cart association
     * @return token response for the authenticated user
     */
    fun login(email: String?, password: String?, sessionId: String? = null): LoginResponse
}

