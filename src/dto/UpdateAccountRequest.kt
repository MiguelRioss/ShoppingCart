package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Partial account update payload.
 */
data class UpdateAccountRequest(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val password: String?,
    val phone: String?,
    val customerType: CustomerType?,
    val deliveryAddress: RegisterAddressRequest?,
    val sameAsDeliveryAddress: Boolean?,
    val invoiceAddress: RegisterAddressRequest?,
    val vatNumber: String?,
    val projectNotes: String?
) {
    companion object {
        fun fromJson(requestBody: String, json: Json = Json): UpdateAccountRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject

            return UpdateAccountRequest(
                firstName = body.stringValue("firstName"),
                lastName = body.stringValue("lastName"),
                email = body.stringValue("email"),
                password = body.stringValue("password"),
                phone = body.stringValue("phone"),
                customerType = CustomerType.from(body.stringValue("customerType")),
                deliveryAddress = RegisterAddressRequest.fromJsonObject(body.objectValue("deliveryAddress")),
                sameAsDeliveryAddress = body.booleanValue("sameAsDeliveryAddress"),
                invoiceAddress = RegisterAddressRequest.fromJsonObject(body.objectValue("invoiceAddress")),
                vatNumber = body.stringValue("vatNumber"),
                projectNotes = body.stringValue("projectNotes")
            )
        }

        private fun JsonObject.objectValue(name: String): JsonObject? =
            this[name]?.takeUnless { it is JsonNull }?.jsonObject

        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

        private fun JsonObject.booleanValue(name: String): Boolean? =
            this[name]?.jsonPrimitive?.booleanOrNull
    }
}
