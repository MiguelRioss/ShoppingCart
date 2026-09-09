package dto.auth

data class LoginResponse(
    val token: String,
    val userId: String,
    val expiresAt: String
)
