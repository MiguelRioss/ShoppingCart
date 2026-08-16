package http.cart

import dto.ClearShoppingCartRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import services.ShoppingCartService

/**
 * Handles POST /cart/clear requests.
 */
class ClearCartHandler(
    private val shoppingCartService: ShoppingCartService,
    private val json: Json = Json
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val clearCartRequest = runCatching {
            ClearShoppingCartRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        if (!clearCartRequest.isValid) {
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
