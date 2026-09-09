package http.auth

import dto.auth.AuthStatusResponse
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import http.toJson
import services.auth.AuthServiceInterface

/**
 * Handles authenticated GET /auth/status requests.
 */
class AuthStatusHandler(
    authService: AuthServiceInterface
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

