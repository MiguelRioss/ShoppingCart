package http.cart

import dto.SaveShoppingCartRequest
import dto.ShoppingCartResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.AuthService
import services.ShoppingCartService

/**
 * Handles POST /cart requests.
 */
class SaveCartHandler(
    private val shoppingCartService: ShoppingCartService,
    private val authService: AuthService? = null,
    private val json: Json = Json
) : RequestHandler {
    /**
     * Creates a cart associated with a session id and product m2 quantities.
     */
    override fun handle(request: HttpRequest): HttpResponse {
        val saveCartRequest = runCatching {
            SaveShoppingCartRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        if (!saveCartRequest.isValid) {
            return HttpError.InvalidJsonRequestBody.toResponse("sessionId and at least one product with quantityM2 are required")
        }

        val cart = runCatching {
            val authenticatedRequest = authService?.let { AuthenticatedRequest.from(request, it) }

            shoppingCartService.createCart(
                sessionId = requireNotNull(saveCartRequest.sessionId),
                products = saveCartRequest.products.map {
                    requireNotNull(it.productId) to requireNotNull(it.quantityM2)
                },
                userId = authenticatedRequest?.user?.id
            )
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse(it.message ?: "Invalid cart products")
        }

        return HttpResponse(201, ShoppingCartResponse(cart).toJson())
    }
}
