package services

import dto.LoginResponse

/**
 * Application service for the login flow.
 *
 * @param authService authentication dependency that validates credentials and issues tokens
 */
class LoginService(
    private val authService: AuthService
) {
    /**
     * Validates login input, creates an auth token, and maps it to an API response.
     *
     * @param email nullable email from the HTTP request body
     * @param password nullable password from the HTTP request body
     * @throws IllegalArgumentException when either field is missing or blank
     */
    fun login(email: String?, password: String?): LoginResponse {
        require(!email.isNullOrBlank() && !password.isNullOrBlank()) {
            "Email and password are required"
        }

        val token = authService.login(email, password)

        return LoginResponse(
            token = token.token,
            userId = token.userId.toString(),
            expiresAt = token.expiresAt.toString()
        )
    }
}
