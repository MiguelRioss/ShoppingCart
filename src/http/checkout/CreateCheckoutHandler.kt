package http.checkout

import dto.checkout.toResponse
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import http.parseCreateCheckoutRequest
import http.serviceExceptionOrNull
import http.toJson
import services.checkout.CheckoutService

class CreateCheckoutHandler(
    private val checkoutService: CheckoutService
) : RequestHandler {

    override fun handle(request: HttpRequest): HttpResponse {
        return try {
            val checkout =
                parseCreateCheckoutRequest(
                    request.body
                ).toDomain()

            val checkoutSession =
                checkoutService.createCheckout(
                    checkout
                )

            HttpResponse(
                201,
                checkoutSession.toResponse().toJson()
            )
        } catch (error: IllegalArgumentException) {
            error.serviceExceptionOrNull()
                ?.let(HttpError::from)
                ?: HttpError.InvalidJsonRequestBody.toResponse(
                    error.message ?: "Invalid checkout request"
                )
        } catch (error: Exception) {
            HttpError.InternalServerError.toResponse(
                description =
                    error.message ?: "Unknown checkout error"
            )
        }
    }
}
