package http.cart

import dto.ShoppingCartResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import services.AuthService
import services.ShoppingCartService

class GetCartHandler(
    private val authService: AuthService,
    private val shoppingCartService: ShoppingCartService
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val authenticatedRequest = AuthenticatedRequest.from(request, authService)
            ?: return HttpError.Unauthorized.toResponse()

        val cart = shoppingCartService.getCartByUserId(authenticatedRequest.user.id)
            ?: return HttpError.NotFound.toResponse("Shopping cart not found")

        return HttpResponse(200, ShoppingCartResponse(cart).toJson())
    }
}
