package dto.user

import domain.user.User
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

fun UserInfoRequest.toResponse(): UserInfoResponse =
    user.toResponse()
