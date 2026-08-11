package dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

data class RegisterAddressRequest(
    val company: String?,
    val addressLine1: String?,
    val addressLine2: String?,
    val townOrCity: String?,
    val postcode: String?,
    val country: String?
) {
    companion object {
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

        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    }
}
