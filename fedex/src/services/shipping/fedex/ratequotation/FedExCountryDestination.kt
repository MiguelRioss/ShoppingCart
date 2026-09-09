package services.shipping.fedex.ratequotation

data class FedExCountryDestination(
    val countryName: String,
    val countryCode: String,
    val city: String,
    val postalCode: String,
    val streetLine: String = "1 Main Street"
)