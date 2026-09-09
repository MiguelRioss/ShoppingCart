/**
 * HTTP handler that returns whether the current request belongs to a logged-in user.
 */
package http.client

import dto.user.toResponse
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import http.toJson
import services.auth.AuthServiceInterface

class GetClientInfoHandler(
    authService: AuthServiceInterface
) : RequestWithAuthHandler(authService) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse =
        HttpResponse(
            statusCode = 200,
            body = request.toUserInfoRequest().toResponse().toJson()
        )
}

