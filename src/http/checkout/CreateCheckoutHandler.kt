package http.checkout

import dto.CheckoutResponse
import dto.CreateCheckoutRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import kotlinx.serialization.json.Json
import services.CheckoutService
import services.ServiceException

class CreateCheckoutHandler(
    private val checkoutService: CheckoutService,
    private val json: Json = Json
) : RequestHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val checkoutRequest = runCatching {
            CreateCheckoutRequest.fromJson(request.body, json)
        }.getOrElse {
            return HttpError.InvalidJsonRequestBody.toResponse()
        }

        if (!checkoutRequest.isValid) {
            return HttpError.InvalidJsonRequestBody.toResponse("sessionId, successUrl, and cancelUrl are required")
        }

        val checkoutSession = runCatching {
            checkoutService.createCheckout(
                sessionId = requireNotNull(checkoutRequest.sessionId),
                successUrl = requireNotNull(checkoutRequest.successUrl),
                cancelUrl = requireNotNull(checkoutRequest.cancelUrl),
                customerEmail = checkoutRequest.customerEmail
            )
        }.getOrElse {
            if (it is ServiceException) {
                return HttpError.from(it)
            }

            return HttpError.InternalServerError.toResponse(description = it.message ?: "Unknown checkout error")
        }

        return HttpResponse(201, CheckoutResponse(checkoutSession).toJson())
    }
}
