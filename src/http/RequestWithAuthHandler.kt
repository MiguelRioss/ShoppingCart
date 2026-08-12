package http

import services.AuthService

/**
 * Base handler for routes that require a valid Authorization bearer token.
 *
 * @param authService service used to resolve the authenticated user
 */
abstract class RequestWithAuthHandler(
    private val authService: AuthService
) : RequestHandler {
    /**
     * Authenticates the request before passing it to the route-specific handler.
     *
     * @param request HTTP request with Authorization: Bearer <token>
     * @return 401 without valid auth, otherwise the route-specific response
     */
    final override fun handle(request: HttpRequest): HttpResponse {
        val authenticatedRequest = AuthenticatedRequest.from(request, authService)
            ?: return HttpError.Unauthorized.toResponse()

        return handleAuthenticated(authenticatedRequest)
    }

    /**
     * Handles a request after the user has been authenticated.
     *
     * @param request request plus authenticated user
     * @return response status and JSON body
     */
    protected abstract fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse
}
