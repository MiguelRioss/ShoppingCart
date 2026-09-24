package dto.auth

import kotlinx.serialization.json.JsonObject
import stringValueIncludingBlank

/** Shared nullable address fields used by account and checkout requests. */
data class AddressFields(
    val company: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val townOrCity: String?,
    val postcode: String?,
    val country: String?
)

/** Extracts address fields while preserving missing and blank input for validation. */
fun JsonObject.toAddressFields(): AddressFields =
    AddressFields(
        company = stringValueIncludingBlank("company"),
        addressLine1 = stringValueIncludingBlank("addressLine1"),
        addressLine2 = stringValueIncludingBlank("addressLine2"),
        townOrCity = stringValueIncludingBlank("townOrCity"),
        postcode = stringValueIncludingBlank("postcode"),
        country = stringValueIncludingBlank("country")
    )
