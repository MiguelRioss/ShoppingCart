package dto.user

import domain.user.User
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import putNullable
import java.time.LocalDateTime
import java.util.UUID

data class UserInfoResponse(
    val id: UUID,
    val email: String,
    val createdAt: LocalDateTime,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val customerType: String? = null,
    val deliveryCompany: String? = null,
    val deliveryAddressLine1: String? = null,
    val deliveryAddressLine2: String? = null,
    val deliveryTownOrCity: String? = null,
    val deliveryPostcode: String? = null,
    val deliveryCountry: String? = null,
    val sameAsDeliveryAddress: Boolean = true,
    val invoiceCompany: String? = null,
    val invoiceAddressLine1: String? = null,
    val invoiceAddressLine2: String? = null,
    val invoiceTownOrCity: String? = null,
    val invoicePostcode: String? = null,
    val invoiceCountry: String? = null,
    val vatNumber: String? = null,
    val projectNotes: String? = null
)

fun User.toResponse() = UserInfoResponse(
    id = id,
    email = email,
    createdAt = createdAt,
    firstName = firstName,
    lastName = lastName,
    phone = phone,
    customerType = customerType,
    deliveryCompany = deliveryCompany,
    deliveryAddressLine1 = deliveryAddressLine1,
    deliveryAddressLine2 = deliveryAddressLine2,
    deliveryTownOrCity = deliveryTownOrCity,
    deliveryPostcode = deliveryPostcode,
    deliveryCountry = deliveryCountry,
    sameAsDeliveryAddress = sameAsDeliveryAddress,
    invoiceCompany = invoiceCompany,
    invoiceAddressLine1 = invoiceAddressLine1,
    invoiceAddressLine2 = invoiceAddressLine2,
    invoiceTownOrCity = invoiceTownOrCity,
    invoicePostcode = invoicePostcode,
    invoiceCountry = invoiceCountry,
    vatNumber = vatNumber,
    projectNotes = projectNotes
)


fun UserInfoResponse.toJson(): String =
    buildJsonObject {
        put("id", JsonPrimitive(id.toString()))
        put("email", JsonPrimitive(email))
        put("createdAt", JsonPrimitive(createdAt.toString()))
        putNullable("firstName", firstName)
        putNullable("lastName", lastName)
        putNullable("phone", phone)
        putNullable("customerType", customerType)
        putNullable("deliveryCompany", deliveryCompany)
        putNullable("deliveryAddressLine1", deliveryAddressLine1)
        putNullable("deliveryAddressLine2", deliveryAddressLine2)
        putNullable("deliveryTownOrCity", deliveryTownOrCity)
        putNullable("deliveryPostcode", deliveryPostcode)
        putNullable("deliveryCountry", deliveryCountry)
        put("sameAsDeliveryAddress", JsonPrimitive(sameAsDeliveryAddress))
        putNullable("invoiceCompany", invoiceCompany)
        putNullable("invoiceAddressLine1", invoiceAddressLine1)
        putNullable("invoiceAddressLine2", invoiceAddressLine2)
        putNullable("invoiceTownOrCity", invoiceTownOrCity)
        putNullable("invoicePostcode", invoicePostcode)
        putNullable("invoiceCountry", invoiceCountry)
        putNullable("vatNumber", vatNumber)
        putNullable("projectNotes", projectNotes)
    }.toString()