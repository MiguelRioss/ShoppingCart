package domain.checkout

import domain.user.User
import java.util.Locale
import shipment.core.ShipmentAddress

data class CheckoutDeliveryAddress(
    val company: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val townOrCity: String?,
    val postcode: String?,
    val country: String?
) {
    fun toShipmentAddress(): ShipmentAddress =
        ShipmentAddress(
            postalCode = requireNotBlank(postcode, "deliveryAddress.postcode"),
            countryCode = normalizeCountryCode(
                requireNotBlank(country, "deliveryAddress.country")
            )
        )

    private fun requireNotBlank(
        value: String?,
        fieldName: String
    ): String {
        require(!value.isNullOrBlank()) {
            "$fieldName is required"
        }

        return value.trim()
    }

    private fun normalizeCountryCode(
        country: String
    ): String =
        if (country.length == 2) {
            country.uppercase()
        } else {
            Locale.getISOCountries()
                .firstOrNull { code ->
                    Locale.Builder()
                        .setRegion(code)
                        .build()
                        .getDisplayCountry(Locale.ENGLISH)
                        .equals(country, ignoreCase = true)
                }
                ?: error("Unknown country: $country")
        }
}

fun User.toCheckoutDeliveryAddress(): CheckoutDeliveryAddress =
    CheckoutDeliveryAddress(
        company = deliveryCompany,
        addressLine1 = deliveryAddressLine1,
        addressLine2 = deliveryAddressLine2,
        townOrCity = deliveryTownOrCity,
        postcode = deliveryPostcode,
        country = deliveryCountry
    )
