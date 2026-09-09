package dto.checkout

import domain.checkout.CheckoutDestination

data class CheckoutDestinationRequest(
    val streetLines: List<String> = emptyList(),
    val countryCode: String?,
    val postalCode: String?,
    val city: String?,
    val currency: String?
) {
    fun toDomain(): CheckoutDestination? {
        if (countryCode.isNullOrBlank() || postalCode.isNullOrBlank()) {
            return null
        }

        return CheckoutDestination(
            streetLines = streetLines,
            countryCode = countryCode.uppercase(),
            postalCode = postalCode,
            city = city,
            currency = currency ?: "eur"
        )
    }
}
