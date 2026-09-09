package dto.auth

data class LoginRequest(
    val email: String?,
    val password: String?,
    val sessionId: String? = null
)
