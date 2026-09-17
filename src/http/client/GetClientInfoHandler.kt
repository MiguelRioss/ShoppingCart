package http.client

import dto.user.toJson
import dto.user.toResponse
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import services.auth.AuthServiceInterface

/**
 * HTTP handler that returns information about the logged-in user.
 */
class GetClientInfoHandler(
    authService: AuthServiceInterface
) : RequestWithAuthHandler(authService) {

    override fun handleAuthenticated(
        request: AuthenticatedRequest
    ): HttpResponse =
        HttpResponse(
            statusCode = 200,
            body =
                request.user
                    .toResponse()
                    .toJson()
        )
}