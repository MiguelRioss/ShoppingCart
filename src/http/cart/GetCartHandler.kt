package http.cart

import dto.ShoppingCartResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpResponse
import http.RequestWithAuthHandler
import services.AuthService
import services.ShoppingCartService

/**
 * Handles authenticated GET /cart requests.
 *
 * @param authService service used to resolve the Authorization bearer token
 * @param shoppingCartService service used to load the authenticated user's cart
 */
class GetCartHandler(
    authService: AuthService,
    private val shoppingCartService: ShoppingCartService
) : RequestWithAuthHandler(authService) {
    /**
     * Returns the cart for the user identified by the bearer token.
     *
     * @param request authenticated HTTP request
     * @return 200 with cart JSON, or 404 when no cart exists
     */
    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        val cart = shoppingCartService.getCartByUserId(request.user.id)
            ?: return HttpError.NotFound.toResponse("Shopping cart not found")

        return HttpResponse(200, ShoppingCartResponse(cart).toJson())
    }
}
