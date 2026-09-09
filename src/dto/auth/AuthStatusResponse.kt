package dto.auth

data class  AuthStatusResponse(
    val authenticated: Boolean,
    val userId: String,
    val email: String
)
