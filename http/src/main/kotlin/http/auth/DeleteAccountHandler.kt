package http.auth

import http.AuthenticatedRequest
import http.HttpError
import http.HttpResponse
import http.RequestWithAuthHandler
import services.auth.AuthServiceInterface
import services.user.UserService

/**
 * Handles authenticated DELETE /account requests.
 */
class DeleteAccountHandler(
    authService: AuthServiceInterface,
    private val userService: UserService
) : RequestWithAuthHandler(authService) {
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        if (!userService.deleteUser(request.user.id)) {
            return HttpError.NotFound.toResponse("User not found")
        }

        return HttpResponse(statusCode = 200, body = "{}")
    }
}

