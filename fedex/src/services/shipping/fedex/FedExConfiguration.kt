package services.shipping.fedex
data class FedExConfiguration(
    val baseUrl: String,
    val clientId: String,
    val clientSecret: String,
    val accountNumber: String
)