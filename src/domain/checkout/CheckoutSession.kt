package domain.checkout

data class CheckoutSession(
    val id: String,
    val provider: String,
    val status: String,
    val url: String?
)


