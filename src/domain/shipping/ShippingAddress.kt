package domain.shipping

data class ShippingAddress(
    val streetLines: List<String>,
    val city: String,
    val postalCode: String,
    val countryCode: String
)
