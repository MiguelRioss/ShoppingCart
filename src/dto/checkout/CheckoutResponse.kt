package dto.checkout

import domain.checkout.CheckoutSession

data class CheckoutResponse(
    val id: String,
    val provider: String,
    val status: String,
    val url: String?
)

fun CheckoutSession.toResponse() = CheckoutResponse(
    id = id,
    provider = provider,
    status = status,
    url = url
)
