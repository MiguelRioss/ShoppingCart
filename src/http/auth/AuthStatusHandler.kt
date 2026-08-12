package http.auth

import dto.AuthStatusResponse
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import services.AuthService

/**
 * Handles authenticated GET /auth/status requests.
 */
class AuthStatusHandler(
    authService: AuthService
) : RequestWithAuthHandler(authService) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse =
        HttpResponse(
            statusCode = 200,
            body = AuthStatusResponse(
                authenticated = true,
                userId = request.user.id.toString(),
                email = request.user.email
            ).toJson()
        )
}
