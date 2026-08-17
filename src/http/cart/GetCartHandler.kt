package http.cart

import dto.ShoppingCartResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import services.AuthService
import services.ShoppingCartService

/**
 * Handles GET /cart requests.
 *
 * @param authService service used to resolve the Authorization bearer token
 * @param shoppingCartService service used to load session or authenticated user carts
 */
class GetCartHandler(
    private val authService: AuthService,
    private val shoppingCartService: ShoppingCartService
) : RequestHandler {
    /**
     * Returns the cart for the session first, then falls back to the authenticated user's cart.
     *
     * @param request HTTP request with optional sessionId query parameter and optional bearer token
     * @return 200 with cart JSON, or 404 when no cart exists
     */
    override fun handle(request: HttpRequest): HttpResponse {
        val sessionCart = request.queryParameter("sessionId")
            ?.takeIf { it.isNotBlank() }
            ?.let { shoppingCartService.getCartBySessionId(it) }
        val authenticatedUser = AuthenticatedRequest.from(request, authService)?.user
        val cart = sessionCart ?: authenticatedUser?.let { shoppingCartService.getCartByUserId(it.id) }
            ?: return HttpError.NotFound.toResponse("Shopping cart not found")

        return HttpResponse(200, ShoppingCartResponse(cart).toJson())
    }
}
