/**
 * Helpers for extracting and validating bearer-token authentication from HTTP requests.
 */
package http

import dto.user.UserInfoRequest
import domain.user.User
import services.auth.AuthServiceInterface

data class AuthenticatedRequest(
    val request: HttpRequest,
    val user: User
) {
    fun toUserInfoRequest(): UserInfoRequest =
        UserInfoRequest(user)

    companion object {
        fun from(request: HttpRequest, authService: AuthServiceInterface): AuthenticatedRequest? {
            val bearerToken = request.header("Authorization") ?: return null
            val user = authService.getUserFromBearerToken(bearerToken) ?: return null

            return AuthenticatedRequest(request, user)
        }
    }
}


