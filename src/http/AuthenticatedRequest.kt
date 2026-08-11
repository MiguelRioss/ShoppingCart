package http

import domain.User
import services.AuthService

data class AuthenticatedRequest(
    val request: HttpRequest,
    val user: User
) {
    companion object {
        fun from(request: HttpRequest, authService: AuthService): AuthenticatedRequest? {
            val bearerToken = request.header("Authorization") ?: return null
            val user = authService.getUserFromBearerToken(bearerToken) ?: return null

            return AuthenticatedRequest(request, user)
        }
    }
}
