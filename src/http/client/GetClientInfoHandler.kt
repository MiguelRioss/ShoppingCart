package http.client

import dto.ClientInfoResponse
import http.AuthenticatedRequest
import http.HttpResponse
import http.RequestWithAuthHandler
import services.AuthService

class GetClientInfoHandler(
    authService: AuthService
) : RequestWithAuthHandler(authService) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse =
        HttpResponse(
            statusCode = 200,
            body = ClientInfoResponse(request.user).toJson()
        )
}
