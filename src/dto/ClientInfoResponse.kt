package dto

import domain.User
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ClientInfoResponse(
    val user: User
) {
    fun toJson(): String =
        buildJsonObject {
            put("userId", JsonPrimitive(user.id.toString()))
            put("email", JsonPrimitive(user.email))
            putNullable("firstName", user.firstName)
            putNullable("lastName", user.lastName)
            putNullable("phone", user.phone)
            putNullable("customerType", user.customerType)
            put(
                "deliveryAddress",
                buildJsonObject {
                    putNullable("company", user.deliveryCompany)
                    putNullable("addressLine1", user.deliveryAddressLine1)
                    putNullable("addressLine2", user.deliveryAddressLine2)
                    putNullable("townOrCity", user.deliveryTownOrCity)
                    putNullable("postcode", user.deliveryPostcode)
                    putNullable("country", user.deliveryCountry)
                }
            )
            put("sameAsDeliveryAddress", JsonPrimitive(user.sameAsDeliveryAddress))
            put(
                "invoiceAddress",
                buildJsonObject {
                    putNullable("company", user.invoiceCompany)
                    putNullable("addressLine1", user.invoiceAddressLine1)
                    putNullable("addressLine2", user.invoiceAddressLine2)
                    putNullable("townOrCity", user.invoiceTownOrCity)
                    putNullable("postcode", user.invoicePostcode)
                    putNullable("country", user.invoiceCountry)
                }
            )
            putNullable("vatNumber", user.vatNumber)
            putNullable("projectNotes", user.projectNotes)
        }.toString()

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(name: String, value: String?) {
        put(name, value?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}
