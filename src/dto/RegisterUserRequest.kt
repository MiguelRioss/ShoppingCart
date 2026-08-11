package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RegisterUserRequest(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val phone: String?,
    val customerType: CustomerType?,
    val deliveryAddress: RegisterAddressRequest?,
    val sameAsDeliveryAddress: Boolean,
    val invoiceAddress: RegisterAddressRequest?,
    val vatNumber: String?,
    val projectNotes: String?
) {
    companion object {
        fun fromJson(requestBody: String, json: Json = Json): RegisterUserRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject
            val sameAsDeliveryAddress = body.booleanValue("sameAsDeliveryAddress") ?: true
            val deliveryAddress = RegisterAddressRequest.fromJsonObject(body.objectValue("deliveryAddress"))

            return RegisterUserRequest(
                firstName = body.stringValue("firstName"),
                lastName = body.stringValue("lastName"),
                email = body.stringValue("email"),
                phone = body.stringValue("phone"),
                customerType = CustomerType.from(body.stringValue("customerType")),
                deliveryAddress = deliveryAddress,
                sameAsDeliveryAddress = sameAsDeliveryAddress,
                invoiceAddress = if (sameAsDeliveryAddress) {
                    deliveryAddress
                } else {
                    RegisterAddressRequest.fromJsonObject(body.objectValue("invoiceAddress"))
                },
                vatNumber = body.stringValue("vatNumber"),
                projectNotes = body.stringValue("projectNotes")
            ).validateRequiredFields()
        }

        private fun JsonObject.objectValue(name: String): JsonObject? =
            this[name]?.takeUnless { it is JsonNull }?.jsonObject

        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

        private fun JsonObject.booleanValue(name: String): Boolean? =
            this[name]?.jsonPrimitive?.booleanOrNull
    }

    private fun validateRequiredFields(): RegisterUserRequest {
        require(!firstName.isNullOrBlank()) { "First name is required" }
        require(!lastName.isNullOrBlank()) { "Last name is required" }
        require(!email.isNullOrBlank()) { "Email is required" }
        require(!phone.isNullOrBlank()) { "Phone is required" }
        require(customerType != null) { "Customer type is required" }
        requireRequiredAddress(deliveryAddress, "Delivery address")

        if (!sameAsDeliveryAddress) {
            requireRequiredAddress(invoiceAddress, "Invoice address")
        }

        return this
    }

    private fun requireRequiredAddress(address: RegisterAddressRequest?, name: String) {
        require(address != null) { "$name is required" }
        require(!address.addressLine1.isNullOrBlank()) { "$name line 1 is required" }
        require(!address.townOrCity.isNullOrBlank()) { "$name town or city is required" }
        require(!address.postcode.isNullOrBlank()) { "$name postcode is required" }
        require(!address.country.isNullOrBlank()) { "$name country is required" }
    }
}
