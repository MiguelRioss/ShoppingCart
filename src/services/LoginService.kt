package services

import dto.LoginResponse

class LoginService(
    private val authService: AuthService
) {
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
