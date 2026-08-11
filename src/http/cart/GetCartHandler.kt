package http.cart

import dto.ShoppingCartResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpResponse
import services.AuthService
import services.ShoppingCartService

class GetCartHandler(
    private val shoppingCartService: ShoppingCartService
            ?: return HttpError.NotFound.toResponse("Shopping cart not found")

        return HttpResponse(200, ShoppingCartResponse(cart).toJson())
    }
}
