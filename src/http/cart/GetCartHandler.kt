package http.cart

import dto.cart.toJson
import dto.cart.toResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import services.auth.AuthServiceInterface
import services.cart.ShoppingCartService

/**
 * Handles GET /cart requests.
 *
 * @param authService service used to resolve the Authorization bearer token
 * @param shoppingCartService service used to load session or authenticated user carts
 */
class GetCartHandler(
    private val authService: AuthServiceInterface,
    private val shoppingCartService: ShoppingCartService
) : RequestHandler {
    /**
     * Returns the explicitly requested session cart, or the latest user cart when no session is supplied.
     *
     * @param request HTTP request with optional sessionId query parameter and optional bearer token
     * @return 200 with cart JSON, or 404 when no cart exists
     */
    override fun handle(request: HttpRequest): HttpResponse {
        val sessionId = request.queryParameter("sessionId")?.takeIf { it.isNotBlank() }
        val authenticatedUser = AuthenticatedRequest.from(request, authService)?.user
        val cart = (if (sessionId != null) shoppingCartService.getCartBySessionId(sessionId)
            else authenticatedUser?.let { shoppingCartService.getCartByUserId(it.id) })
            ?: return HttpError.NotFound.toResponse("Shopping cart not found")

        return HttpResponse(200, cart.toResponse().toJson())
    }
}

