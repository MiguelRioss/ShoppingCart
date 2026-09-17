package domain.checkout.payment

data class PaymentWebhookRequest(
    val payload: String,
    val signature: String?,
    val headers: Map<String, String> = emptyMap()
)