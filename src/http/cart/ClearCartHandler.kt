package http.cart

import dto.cart.parseClearShoppingCartRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import services.cart.ShoppingCartService

/**
 * Handles POST /cart/clear requests.
 */
class ClearCartHandler(
    private val shoppingCartService: ShoppingCartService,
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val clearCartRequest = runCatching {
            parseClearShoppingCartRequest(request.body)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        if (clearCartRequest.sessionId.isNullOrBlank()) {
            return HttpError.InvalidJsonRequestBody.toResponse("sessionId is required")
        }

        val cleared = shoppingCartService.clearCartBySessionId(requireNotNull(clearCartRequest.sessionId))
        if (!cleared) {
            return HttpError.NotFound.toResponse("Shopping cart not found for sessionId")
        }

        return HttpResponse(
            statusCode = 200,
            body = buildJsonObject {
                put("message", JsonPrimitive("Shopping cart cleared"))
                put("sessionId", JsonPrimitive(clearCartRequest.sessionId))
            }.toString()
        )
    }
}

