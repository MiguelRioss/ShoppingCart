package http.checkout

import domain.checkout.toCheckoutDeliveryAddress
import domain.checkout.toResponse
import dto.checkout.parseCreateCheckoutRequest
import dto.checkout.toDomain
import dto.checkout.toJson
import http.AuthenticatedRequest
import http.HttpError
import http.HttpRequest
import http.HttpResponse
import http.RequestHandler
import http.serviceExceptionOrNull
import services.auth.AuthServiceInterface
import services.checkout.core.CheckoutService

/** Handles `POST /checkout` and creates a hosted payment session for a saved cart. */
class CreateCheckoutHandler(
    private val checkoutService: CheckoutService,
    private val authService: AuthServiceInterface? = null
) : RequestHandler {

    override fun handle(request: HttpRequest): HttpResponse {
        return try {
            val checkout =
                parseCreateCheckoutRequest(
                    request.body
                ).toDomain()
                    .let { checkoutRequest ->
                        val authenticatedUser =
                            authService
                                ?.let {
                                    AuthenticatedRequest.from(request, it)
                                }
                                ?.user

                        authenticatedUser
                            ?.let {
                                checkoutRequest.copy(
                                    deliveryAddress =
                                        it.toCheckoutDeliveryAddress()
                                )
                            }
                            ?: checkoutRequest
                    }

            val checkoutSession =
                checkoutService.createCheckoutSession(
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
