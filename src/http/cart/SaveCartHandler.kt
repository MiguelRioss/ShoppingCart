package http.cart

import dto.cart.SaveShoppingCartRequest
import dto.cart.parseSaveShoppingCartRequest
import dto.cart.toEntity
import dto.cart.toJson
import dto.cart.toResponse
import http.AuthenticatedRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.auth.AuthServiceInterface
import services.common.ServiceException
import services.cart.ShoppingCartService

/**
 * Handles POST /cart requests.
 */
class SaveCartHandler(
    private val shoppingCartService: ShoppingCartService,
    private val authService: AuthServiceInterface? = null,
) : RequestHandler {
    /**
     * Creates a cart associated with a session id and product m2 quantities.
     */
    override fun handle(request: HttpRequest): HttpResponse {
        val saveCartRequest = runCatching {
            parseSaveShoppingCartRequest(request.body)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        if (!saveCartRequest.isValid()) {
            return HttpError.InvalidJsonRequestBody.toResponse(
                "sessionId and at least one valid product are required"
            )
        }

        val cart = runCatching {
            val authenticatedRequest = authService?.let { AuthenticatedRequest.from(request, it) }

            shoppingCartService.createCart(
                sessionId = requireNotNull(saveCartRequest.sessionId),
                products = saveCartRequest.products.map { it.toEntity() },
                userId = authenticatedRequest?.user?.id
            )
        }.getOrElse {
            if (it is ServiceException) {
                return HttpError.from(it)
            }

            return HttpError.InvalidJsonRequestBody.toResponse(it.message ?: "Invalid cart products")
        }

        return HttpResponse(201, cart.toResponse().toJson())
    }

    private fun SaveShoppingCartRequest.isValid(): Boolean =
        !sessionId.isNullOrBlank() &&
                products.isNotEmpty() &&
                products.all {
                    it.productId != null
                }
}
