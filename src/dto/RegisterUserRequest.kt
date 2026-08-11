package dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Full user-registration payload.
 *
 * @param firstName required first name
 * @param lastName required last name
 * @param email required email address
 * @param password required password
 * @param phone required phone number
 * @param customerType required customer classification
 * @param deliveryAddress required delivery address
 * @param sameAsDeliveryAddress whether invoice address should reuse delivery address
 * @param invoiceAddress required only when [sameAsDeliveryAddress] is false
 * @param vatNumber optional VAT number for business customers
 * @param projectNotes optional notes about the customer's project
 */
data class RegisterUserRequest(
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val password: String?,
    val phone: String?,
    val customerType: CustomerType?,
    val deliveryAddress: RegisterAddressRequest?,
    val sameAsDeliveryAddress: Boolean,
    val invoiceAddress: RegisterAddressRequest?,
    val vatNumber: String?,
    val projectNotes: String?
) {
    companion object {
        /**
         * Parses and validates a registration request from raw JSON.
         *
         * @param requestBody raw HTTP body
         * @param json JSON parser, injectable for tests
         * @throws IllegalArgumentException when required fields are missing
         * @throws kotlinx.serialization.SerializationException when the body is not valid JSON
         */
        fun fromJson(requestBody: String, json: Json = Json): RegisterUserRequest {
            val body = json.parseToJsonElement(requestBody).jsonObject
            val sameAsDeliveryAddress = body.booleanValue("sameAsDeliveryAddress") ?: true
            val deliveryAddress = RegisterAddressRequest.fromJsonObject(body.objectValue("deliveryAddress"))

            return RegisterUserRequest(
                firstName = body.stringValue("firstName"),
                lastName = body.stringValue("lastName"),
                email = body.stringValue("email"),
                password = body.stringValue("password"),
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

        /**
         * Reads a nullable nested JSON object, treating explicit JSON null as absent.
         */
        private fun JsonObject.objectValue(name: String): JsonObject? =
            this[name]?.takeUnless { it is JsonNull }?.jsonObject

        /**
         * Reads a nullable string field, treating explicit JSON null as absent.
         */
        private fun JsonObject.stringValue(name: String): String? =
            this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

        /**
         * Reads a nullable boolean field.
         */
        private fun JsonObject.booleanValue(name: String): Boolean? =
            this[name]?.jsonPrimitive?.booleanOrNull
    }

    /**
     * Enforces all fields needed to create a complete registration request.
     */
    private fun validateRequiredFields(): RegisterUserRequest {
        require(!firstName.isNullOrBlank()) { "First name is required" }
        require(!lastName.isNullOrBlank()) { "Last name is required" }
        require(!email.isNullOrBlank()) { "Email is required" }
        require(!password.isNullOrBlank()) { "Password is required" }
        require(!phone.isNullOrBlank()) { "Phone is required" }
        require(customerType != null) { "Customer type is required" }
        requireRequiredAddress(deliveryAddress, "Delivery address")

        if (!sameAsDeliveryAddress) {
            requireRequiredAddress(invoiceAddress, "Invoice address")
        }

        return this
    }

    /**
     * Enforces the required fields for either delivery or invoice addresses.
     */
    private fun requireRequiredAddress(address: RegisterAddressRequest?, name: String) {
        require(address != null) { "$name is required" }
        require(!address.addressLine1.isNullOrBlank()) { "$name line 1 is required" }
        require(!address.townOrCity.isNullOrBlank()) { "$name town or city is required" }
        require(!address.postcode.isNullOrBlank()) { "$name postcode is required" }
        require(!address.country.isNullOrBlank()) { "$name country is required" }
    }
}
