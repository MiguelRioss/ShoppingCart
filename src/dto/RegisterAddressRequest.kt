package dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Address payload used inside a registration request.
 *
 * @param company optional company name
 * @param addressLine1 first address line, required by parent registration validation
 * @param addressLine2 optional second address line
 * @param townOrCity town or city, required by parent registration validation
 * @param postcode postal code, required by parent registration validation
 * @param country country, required by parent registration validation
 */
data class RegisterAddressRequest(
    val company: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val townOrCity: String?,
    val postcode: String?,
    val country: String?
) {
    companion object {
        /**
         * Parses an address object from a parent JSON payload.
         *
         * @param body JSON object for the address, or null when the address is absent
         * @return parsed address, or null when [body] is null
         */
        fun fromJsonObject(body: JsonObject?): RegisterAddressRequest? {
            if (body == null) return null

            return RegisterAddressRequest(
                company = body.stringValue("company"),
                addressLine1 = body.stringValue("addressLine1"),
                addressLine2 = body.stringValue("addressLine2"),
                townOrCity = body.stringValue("townOrCity"),
                postcode = body.stringValue("postcode"),
                country = body.stringValue("country")
            )
        }

        /**
         * Reads a nullable string field, treating explicit JSON null as absent.
         */
        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    }
}
