package domain.checkout

import domain.user.User
import java.util.Locale
import domain.shipment.ShipmentAddress

/**
 * Customer delivery address used to calculate shipping during checkout.
 * Carrier mapping currently requires a postcode and ISO country code.
 */
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
            addressLine1 =
                requireNotBlank(
                    addressLine1,
                    "deliveryAddress.addressLine1"
                ),
            addressLine2 = addressLine2?.trim()?.takeIf(String::isNotEmpty),
            city =
                requireNotBlank(
                    townOrCity,
                    "deliveryAddress.townOrCity"
                ),
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

/** Maps delivery fields saved on a user account into checkout input. */
fun User.toCheckoutDeliveryAddress(): CheckoutDeliveryAddress =
    CheckoutDeliveryAddress(
        company = deliveryCompany,
        addressLine1 = deliveryAddressLine1,
        addressLine2 = deliveryAddressLine2,
        townOrCity = deliveryTownOrCity,
        postcode = deliveryPostcode,
        country = deliveryCountry
    )
