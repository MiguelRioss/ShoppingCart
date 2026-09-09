package dto.checkout

import domain.checkout.Checkout

data class CheckoutRequest(
    val sessionId: String?,
    val successUrl: String?,
    val cancelUrl: String?,
    val customerEmail: String?,
    val destination: CheckoutDestinationRequest?,
    val shippingCharge: CheckoutShippingChargeRequest? = null
) {
    fun toDomain(): Checkout =
        Checkout(
            sessionId = sessionId.required("sessionId"),
            successUrl = successUrl.required("successUrl"),
            cancelUrl = cancelUrl.required("cancelUrl"),
            customerEmail = customerEmail,
            destination = destination?.toDomain(),
            shippingCharge = shippingCharge?.toDomain()
        )

    private fun String?.required(fieldName: String): String =
        require(!this.isNullOrBlank()) {
            "$fieldName is required"
        }.let { this }
}
