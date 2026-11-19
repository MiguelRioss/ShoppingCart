package domain.checkout.payment

/** Raw webhook input retained unchanged for provider signature verification. */
data class PaymentWebhookRequest(
    val payload: String,
    val signature: String?,
    val headers: Map<String, String> = emptyMap()
)
