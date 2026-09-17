package domain.checkout

import dto.checkout.CheckoutResponse
import services.checkout.payment.core.PaymentProviderType

/**
 * Provider-agnostic checkout session returned by the checkout service.
 *
 * @property id checkout session id returned by the payment provider
 * @property provider payment provider that created the session
 * @property status normalized checkout session status
 * @property url hosted checkout URL, when the provider supplies one
 */
data class CheckoutSession(
    val id: String,
    val provider: PaymentProviderType,
    val status: CheckoutSessionStatus,
    val url: String?
)

/**
 * Converts the domain checkout session into the public HTTP response DTO.
 */
fun CheckoutSession.toResponse() = CheckoutResponse(
    id = id,
    provider = provider.responseValue,
    status = status.responseValue,
    url = url
)
