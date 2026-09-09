package services.shipping.fedex.ratequotation

data class FedExCountryQuotationResult(
    val countryName: String,
    val countryCode: String,
    val city: String,
    val postalCode: String,
    val success: Boolean,
    val attempts: Int,
    val response: String?,
    val error: String?
)