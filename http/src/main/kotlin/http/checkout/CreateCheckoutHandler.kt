package http.checkout

import domain.checkout.toCheckoutDeliveryAddress
import domain.checkout.toResponse
import dto.checkout.parseCreateCheckoutRequest
import dto.checkout.toDomain
import dto.checkout.toJson
import http.AuthenticatedRequest
import http.HttpError
import http.HttpResponse
import http.RequestWithAuthHandler
import http.serviceExceptionOrNull
import java.util.logging.Level
import java.util.logging.Logger
import services.auth.AuthServiceInterface
import services.checkout.core.CheckoutService

/** Handles `POST /checkout` and creates a hosted payment session for a saved cart. */
class CreateCheckoutHandler(
    private val checkoutService: CheckoutService,
    authService: AuthServiceInterface
) : RequestWithAuthHandler(authService) {
    private val logger = Logger.getLogger(javaClass.name)

    override fun handleAuthenticated(request: AuthenticatedRequest): HttpResponse {
        return try {
            val checkout =
                parseCreateCheckoutRequest(
                    request.request.body
                ).toDomain()
                    .copy(
                        deliveryAddress = request.user.toCheckoutDeliveryAddress(),
                        customerEmail = request.user.email,
                        userId = request.user.id,
                        customerFirstName = request.user.firstName,
                        customerLastName = request.user.lastName,
                        customerPhone = request.user.phone
                    )

            val checkoutSession =
                checkoutService.createCheckoutSession(
                    checkout
                )

            HttpResponse(
                201,
                checkoutSession.toResponse().toJson()
            )
        } catch (error: IllegalArgumentException) {
            val serviceError = error.serviceExceptionOrNull()
            if (serviceError == null) {
                logger.log(Level.WARNING, "Checkout request failed: ${error.message ?: "invalid request"}", error)
                HttpError.InvalidJsonRequestBody.toResponse(error.message ?: "Invalid checkout request")
            } else {
                HttpError.from(serviceError)
            }
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Checkout session creation failed", error)
            HttpError.InternalServerError.toResponse(
                description =
                    error.message ?: "Unknown checkout error"
            )
        }
    }
}
