package domain.checkout

data class CheckoutDestination(
    val streetLines: List<String> = emptyList(),
    val countryCode: String,
    val postalCode: String,
    val city: String? = null,
    val currency: String = "eur"
)


